package expo.modules.autoassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.lang.ref.WeakReference

class AutoAssistService : AccessibilityService() {
  enum class Phase {
    IDLE, WAIT_APP, SKIP_ADS, GO_EARN, SIGN, DONE
  }

  companion object {
    private var instanceRef: WeakReference<AutoAssistService>? = null

    @Volatile
    var phase: Phase = Phase.IDLE

    @Volatile
    var targetPackage: String = "com.kuaishou.nebula"

    var onProgress: ((step: String, message: String, ok: Boolean) -> Unit)? = null

    fun get(): AutoAssistService? = instanceRef?.get()

    fun startTask(packageName: String) {
      targetPackage = packageName
      val svc = get()
      if (svc == null) {
        onProgress?.invoke("service", "无障碍服务未连接，请关闭后再重新开启「工具箱自动签到」", false)
        phase = Phase.IDLE
        return
      }
      svc.beginTask()
    }

    fun stopTask() {
      phase = Phase.IDLE
      get()?.stopPolling()
    }
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private var lastClickAt = 0L
  private var pollCount = 0
  private var adsClicks = 0
  private var earnTapCount = 0
  private var signClicked = false
  private var dumped = false

  private val enterAdsRunnable = Runnable {
    if (phase == Phase.IDLE) return@Runnable
    phase = Phase.SKIP_ADS
    pollCount = 0
    emit("ads", "开始自动跳过广告 / 弹窗", true)
    startPolling()
  }

  private val timeoutRunnable = Runnable {
    if (phase == Phase.IDLE || phase == Phase.DONE) return@Runnable
    finishTask(signClicked, "流程超时结束（签到=${if (signClicked) "可能已点" else "未确认"}）")
  }

  private fun pollIntervalMs(): Long = when (phase) {
    Phase.GO_EARN -> 220L
    Phase.SIGN -> 180L
    else -> 200L
  }

  private val pollRunnable = object : Runnable {
    override fun run() {
      if (phase == Phase.IDLE || phase == Phase.DONE) return
      pollCount += 1
      runPhaseOnce()
      if (phase != Phase.IDLE && phase != Phase.DONE) {
        mainHandler.postDelayed(this, pollIntervalMs())
      }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instanceRef = WeakReference(this)
    emit("service", "无障碍服务已连接", true)
  }

  override fun onDestroy() {
    stopPolling()
    instanceRef = null
    super.onDestroy()
  }

  override fun onInterrupt() {}

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (phase == Phase.IDLE || phase == Phase.DONE) return
    // 不依赖事件也能靠轮询推进；有事件时多点一次
    runPhaseOnce()
  }

  private fun beginTask() {
    clearTaskCallbacks()
    pollCount = 0
    adsClicks = 0
    earnTapCount = 0
    signClicked = false
    dumped = false
    phase = Phase.WAIT_APP
    emit("open", "正在打开快手极速版…", true)
    launchTarget()
    // 等首屏起来再点；过早点 Tab 会落在开屏上然后直接进签到，后续全失效
    mainHandler.postDelayed(enterAdsRunnable, 1200L)
    mainHandler.postDelayed(timeoutRunnable, 25000L)
  }

  private fun startPolling() {
    mainHandler.removeCallbacks(pollRunnable)
    mainHandler.post(pollRunnable)
  }

  private fun clearTaskCallbacks() {
    mainHandler.removeCallbacks(pollRunnable)
    mainHandler.removeCallbacks(enterAdsRunnable)
    mainHandler.removeCallbacks(timeoutRunnable)
  }

  private fun stopPolling() {
    clearTaskCallbacks()
  }

  private fun launchTarget() {
    try {
      val intent = packageManager.getLaunchIntentForPackage(targetPackage)
      if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
      } else {
        emit("open", "未安装目标应用: $targetPackage", false)
        phase = Phase.IDLE
      }
    } catch (e: Exception) {
      emit("open", "启动失败: ${e.message}", false)
      phase = Phase.IDLE
    }
  }

  private fun runPhaseOnce() {
    when (phase) {
      Phase.WAIT_APP -> {
        clickByKeywords(listOf("跳过", "关闭广告", "我知道了"))
      }
      Phase.SKIP_ADS -> {
        val hit = clickByKeywords(
          listOf("跳过", "关闭广告", "我知道了", "以后再说", "禁止", "取消")
        )
        if (hit) {
          adsClicks += 1
          emit("ads", "已处理弹窗/广告 ($adsClicks)", true)
        }
        // 给首页 Tab 露出时间：约 0.8–1.2s，点到广告则多留一轮
        val ready = (adsClicks > 0 && pollCount >= 3) || pollCount >= 5
        if (ready) {
          phase = Phase.GO_EARN
          pollCount = 0
          emit("earn", "尝试进入「去赚钱」", true)
          maybeDumpNodes()
        }
      }
      Phase.GO_EARN -> {
        // 停留在本阶段多次点 Tab，避免首屏未就绪点空后永远不再试
        if (pollCount == 1 || pollCount == 3 || pollCount == 5 || pollCount == 7) {
          val ok = tapEarnTab(attempt = earnTapCount)
          earnTapCount += 1
          emit("earn", "点击赚钱 Tab (#$earnTapCount)${if (ok) "" else "（手势未接受，将重试）"}", ok)
        }
        // 已看到签到相关文案，或已点过几次并等待页面切换
        val signVisible = hasAnyKeyword(listOf("立即签到", "签到领金币", "今日签到", "签到"))
        val pageReady = earnTapCount >= 2 && pollCount >= 8
        if (signVisible || pageReady || pollCount >= 12) {
          phase = Phase.SIGN
          pollCount = 0
          emit("sign", if (signVisible) "已进入赚钱页，尝试签到" else "尝试点击签到", true)
          maybeDumpNodes()
        }
      }
      Phase.SIGN -> {
        if (!signClicked) {
          // 赚钱页若还没切过来，顺手再点一次 Tab
          if (pollCount == 1 || pollCount == 4) {
            tapEarnTab(attempt = earnTapCount)
            earnTapCount += 1
          }
          val hit = clickByKeywords(
            listOf("立即签到", "签到领金币", "签到", "打卡", "领金币")
          )
          if (hit) {
            signClicked = true
            emit("sign", "已点击签到", true)
            mainHandler.postDelayed({
              clickByKeywords(listOf("我知道了", "开心收下", "知道了", "确定", "关闭"))
              finishTask(true, "签到流程已执行")
            }, 600L)
            return
          } else if (pollCount == 6) {
            tapRatio(0.50f, 0.22f)
            emit("sign", "未识别签到文案，已尝试点击上方区域", true)
          } else if (pollCount == 10) {
            tapRatio(0.50f, 0.30f)
          } else if (pollCount == 14) {
            tapRatio(0.50f, 0.38f)
          }
        } else if (pollCount >= 2) {
          finishTask(true, "签到流程已执行")
          return
        }
        if (pollCount >= 20) {
          finishTask(signClicked, if (signClicked) "签到流程已执行" else "未稳定点到签到，请看日志或手动补点")
        }
      }
      else -> Unit
    }
  }

  private fun finishTask(ok: Boolean, message: String) {
    phase = Phase.DONE
    clearTaskCallbacks()
    emit("done", message, ok)
    mainHandler.postDelayed({ phase = Phase.IDLE }, 500L)
  }

  private fun maybeDumpNodes() {
    if (dumped) return
    dumped = true
    val sample = collectVisibleTexts(24)
    if (sample.isNotEmpty()) {
      emit("ads", "界面可见文案: ${sample.joinToString(" / ")}", true)
    } else {
      emit("ads", "未读到界面文案（快手可能限制了无障碍节点）", false)
    }
  }

  private fun collectVisibleTexts(limit: Int): List<String> {
    val out = LinkedHashSet<String>()
    for (root in getRoots()) {
      walk(root) { node ->
        val t = (node.text?.toString() ?: "").trim()
        val d = (node.contentDescription?.toString() ?: "").trim()
        if (t.isNotEmpty() && t.length <= 20) out.add(t)
        if (d.isNotEmpty() && d.length <= 20) out.add(d)
        out.size >= limit
      }
    }
    return out.take(limit)
  }

  private fun hasAnyKeyword(keywords: List<String>): Boolean {
    for (root in getRoots()) {
      var found = false
      walk(root) { node ->
        val hay = "${node.text ?: ""} ${node.contentDescription ?: ""}"
        for (key in keywords) {
          if (hay.contains(key) && hay.length <= 28) {
            found = true
            return@walk true
          }
        }
        false
      }
      if (found) return true
    }
    return false
  }

  private fun clickByKeywords(keywords: List<String>): Boolean {
    for (root in getRoots()) {
      val hit = findAndClick(root, keywords)
      if (hit) return true
    }
    return false
  }

  private fun findAndClick(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
    var clicked = false
    walk(root) { node ->
      val t = (node.text?.toString() ?: "")
      val d = (node.contentDescription?.toString() ?: "")
      val hay = "$t $d"
      for (key in keywords) {
        if (hay.contains(key)) {
          // 过滤说明长文案
          if (hay.length > 24 && key.length <= 4) continue
          if (clickNode(node)) {
            clicked = true
            return@walk true
          }
        }
      }
      false
    }
    return clicked
  }

  private fun walk(
    node: AccessibilityNodeInfo?,
    visitor: (AccessibilityNodeInfo) -> Boolean
  ) {
    if (node == null) return
    if (visitor(node)) return
    for (i in 0 until node.childCount) {
      val child = try {
        node.getChild(i)
      } catch (_: Exception) {
        null
      }
      walk(child, visitor)
    }
  }

  private fun getRoots(): List<AccessibilityNodeInfo> {
    val roots = ArrayList<AccessibilityNodeInfo>()
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val ws = windows
        if (ws != null) {
          for (w in ws) {
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
              w.type == AccessibilityWindowInfo.TYPE_SYSTEM
            ) {
              w.root?.let { roots.add(it) }
            }
          }
        }
      }
    } catch (_: Exception) {
    }
    try {
      rootInActiveWindow?.let { roots.add(it) }
    } catch (_: Exception) {
    }
    return roots
  }

  private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
    if (node == null) return false
    val now = System.currentTimeMillis()
    if (now - lastClickAt < 160L) return false

    var current: AccessibilityNodeInfo? = node
    var depth = 0
    while (current != null && depth < 6) {
      if (current.isClickable || current.isCheckable) {
        if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
          lastClickAt = now
          return true
        }
      }
      current = current.parent
      depth += 1
    }

    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    if (bounds.isEmpty) return false
    return tap(bounds.exactCenterX(), bounds.exactCenterY())
  }

  private fun tapEarnTab(attempt: Int = 0): Boolean {
    // 实机：去赚钱 ≈ x0.90；图标带 y≈0.94。多组微偏移提高命中
    val points = arrayOf(
      0.90f to 0.94f,
      0.89f to 0.93f,
      0.91f to 0.94f,
      0.90f to 0.92f,
    )
    val (xr, yr) = points[attempt % points.size]
    return tapRatio(xr, yr)
  }

  private fun tapRatio(xr: Float, yr: Float): Boolean {
    val dm = resources.displayMetrics
    val x = dm.widthPixels * xr
    val y = dm.heightPixels * yr
    return tap(x, y)
  }

  private fun tap(x: Float, y: Float): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastClickAt < 220L) return false
    val path = Path()
    path.moveTo(x, y)
    // 略加长按压，部分机型过短手势会被吞掉
    val stroke = GestureDescription.StrokeDescription(path, 0, 80)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    val ok = dispatchGesture(gesture, null, null)
    if (ok) lastClickAt = now
    return ok
  }

  private fun emit(step: String, message: String, ok: Boolean) {
    mainHandler.post {
      onProgress?.invoke(step, message, ok)
    }
  }
}
