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
  private var earnClicked = false
  private var signClicked = false
  private var dumped = false

  private fun pollIntervalMs(): Long = when (phase) {
    Phase.GO_EARN, Phase.SIGN -> 120L
    else -> 180L
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
    stopPolling()
    pollCount = 0
    adsClicks = 0
    earnClicked = false
    signClicked = false
    dumped = false
    phase = Phase.WAIT_APP
    emit("open", "正在打开快手极速版…", true)
    launchTarget()
    // 冷启动也尽快进广告/赚钱；首屏慢时靠轮询补点
    mainHandler.postDelayed({
      if (phase == Phase.IDLE) return@postDelayed
      phase = Phase.SKIP_ADS
      pollCount = 0
      emit("ads", "开始自动跳过广告 / 弹窗", true)
      startPolling()
    }, 500L)

    // 整体超时保护
    mainHandler.postDelayed({
      if (phase == Phase.IDLE || phase == Phase.DONE) return@postDelayed
      finishTask(signClicked, "流程超时结束（签到=${if (signClicked) "可能已点" else "未确认"}）")
    }, 18000L)
  }

  private fun startPolling() {
    mainHandler.removeCallbacks(pollRunnable)
    mainHandler.post(pollRunnable)
  }

  private fun stopPolling() {
    mainHandler.removeCallbacks(pollRunnable)
    mainHandler.removeCallbacksAndMessages(null)
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
        // 启动后抢先点掉开屏广告
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
        // 最多再补一轮，然后立刻切赚钱 Tab（空等是慢的主因）
        if (hit && pollCount < 2) return
        phase = Phase.GO_EARN
        pollCount = 0
        emit("earn", "尝试进入「去赚钱」", true)
      }
      Phase.GO_EARN -> {
        if (!earnClicked) {
          // 按实机截图校准：5 Tab 最右侧「去赚钱」≈ x0.90；
          // 图标/文字带约在 y0.94（再往下是空白/手势条，点 0.95+ 会落空）
          tapEarnTab()
          earnClicked = true
          emit("earn", "已点击右下角赚钱 Tab", true)
          mainHandler.postDelayed({
            if (phase == Phase.GO_EARN || phase == Phase.SIGN) {
              tapEarnTab(retry = true)
            }
          }, 200L)
        }
        // 同一次轮询即可进入签到探测
        if (earnClicked) {
          phase = Phase.SIGN
          pollCount = 0
          emit("sign", "尝试点击签到", true)
        }
      }
      Phase.SIGN -> {
        if (!signClicked) {
          val hit = clickByKeywords(
            listOf("立即签到", "签到领金币", "签到", "打卡", "领金币")
          )
          if (hit) {
            signClicked = true
            emit("sign", "已点击签到", true)
            mainHandler.postDelayed({
              clickByKeywords(listOf("我知道了", "开心收下", "知道了", "确定", "关闭"))
              finishTask(true, "签到流程已执行")
            }, 500L)
            return
          } else if (pollCount == 2) {
            tapRatio(0.50f, 0.22f)
            emit("sign", "未识别签到文案，已尝试点击上方区域", true)
          } else if (pollCount == 4) {
            tapRatio(0.50f, 0.30f)
          }
        } else if (pollCount >= 2) {
          finishTask(true, "签到流程已执行")
          return
        }
        if (pollCount >= 8) {
          finishTask(signClicked, if (signClicked) "签到流程已执行" else "未稳定点到签到，请看日志或手动补点")
        }
      }
      else -> Unit
    }
  }

  private fun finishTask(ok: Boolean, message: String) {
    phase = Phase.DONE
    stopPolling()
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

  private fun tapEarnTab(retry: Boolean = false): Boolean {
    // 截图像素峰：去赚钱中心约 0.896；主点图标带 0.94，补点略上移防导航条遮挡
    return if (!retry) {
      tapRatio(0.90f, 0.94f)
    } else {
      tapRatio(0.89f, 0.93f)
    }
  }

  private fun tapRatio(xr: Float, yr: Float): Boolean {
    val dm = resources.displayMetrics
    val x = dm.widthPixels * xr
    val y = dm.heightPixels * yr
    return tap(x, y)
  }

  private fun tap(x: Float, y: Float): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastClickAt < 160L) return false
    val path = Path()
    path.moveTo(x, y)
    val stroke = GestureDescription.StrokeDescription(path, 0, 40)
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
