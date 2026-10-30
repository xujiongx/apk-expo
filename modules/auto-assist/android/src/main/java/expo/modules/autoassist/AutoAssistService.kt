package expo.modules.autoassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class AutoAssistService : AccessibilityService() {
  enum class Phase {
    IDLE, LAUNCHED, SKIP_ADS, GO_EARN, SIGN, DONE
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
      phase = Phase.LAUNCHED
      val svc = get()
      if (svc == null) {
        onProgress?.invoke("service", "无障碍服务未连接，请重新开关权限后再试", false)
        phase = Phase.IDLE
        return
      }
      svc.emit("open", "任务已启动，正在打开应用", true)
      svc.launchTarget()
      svc.schedulePhase(Phase.SKIP_ADS, 3500L)
    }

    fun stopTask() {
      phase = Phase.IDLE
      get()?.mainHandler?.removeCallbacksAndMessages(null)
    }
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private var lastClickAt = 0L

  override fun onServiceConnected() {
    super.onServiceConnected()
    instanceRef = WeakReference(this)
    emit("service", "无障碍服务已连接", true)
  }

  override fun onDestroy() {
    instanceRef = null
    super.onDestroy()
  }

  override fun onInterrupt() {}

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (phase == Phase.IDLE || phase == Phase.DONE) return
    val pkg = event?.packageName?.toString() ?: return
    if (pkg != targetPackage) return
    tick()
  }

  private fun launchTarget() {
    try {
      val intent = packageManager.getLaunchIntentForPackage(targetPackage)
      if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

  private fun schedulePhase(next: Phase, delayMs: Long) {
    mainHandler.postDelayed({
      if (phase == Phase.IDLE) return@postDelayed
      phase = next
      when (next) {
        Phase.SKIP_ADS -> {
          emit("ads", "开始跳过广告", true)
          runSkipAdsRound(0)
        }
        Phase.GO_EARN -> {
          emit("earn", "尝试进入「去赚钱」", true)
          tryClickTexts(listOf("去赚钱", "赚钱", "福利"), "earn")
          schedulePhase(Phase.SIGN, 2500L)
        }
        Phase.SIGN -> {
          emit("sign", "尝试点击签到", true)
          val ok = tryClickTexts(
            listOf("立即签到", "签到领金币", "签到", "打卡"),
            "sign"
          )
          mainHandler.postDelayed({
            tryClickTexts(
              listOf("我知道了", "开心收下", "关闭", "确定", "知道了"),
              "sign"
            )
            phase = Phase.DONE
            emit(
              "done",
              if (ok) "签到流程已执行" else "未找到签到按钮，可能已签到或页面改版",
              ok
            )
            mainHandler.postDelayed({ phase = Phase.IDLE }, 800L)
          }, 1500L)
        }
        else -> Unit
      }
    }, delayMs)
  }

  private fun runSkipAdsRound(round: Int) {
    if (phase != Phase.SKIP_ADS) return
    tryClickTexts(
      listOf("跳过", "关闭广告", "关闭", "我知道了", "以后再说", "取消", "禁止"),
      "ads"
    )
    if (round < 5) {
      mainHandler.postDelayed({ runSkipAdsRound(round + 1) }, 900L)
    } else {
      schedulePhase(Phase.GO_EARN, 600L)
    }
  }

  private fun tick() {
    when (phase) {
      Phase.SKIP_ADS -> tryClickTexts(
        listOf("跳过", "关闭广告", "关闭", "我知道了"),
        "ads",
        quiet = true
      )
      Phase.GO_EARN -> tryClickTexts(listOf("去赚钱", "赚钱"), "earn", quiet = true)
      Phase.SIGN -> tryClickTexts(
        listOf("立即签到", "签到领金币", "签到"),
        "sign",
        quiet = true
      )
      else -> Unit
    }
  }

  private fun tryClickTexts(
    texts: List<String>,
    step: String,
    quiet: Boolean = false
  ): Boolean {
    val root = rootInActiveWindow ?: return false
    for (text in texts) {
      val nodes = try {
        root.findAccessibilityNodeInfosByText(text)
      } catch (_: Exception) {
        null
      } ?: continue

      for (node in nodes) {
        val nodeText =
          (node.text?.toString() ?: "") + (node.contentDescription?.toString() ?: "")
        if (nodeText.length > 16 && text.length <= 4) continue
        if (clickNode(node)) {
          if (!quiet) emit(step, "已点击「$text」", true)
          return true
        }
      }
    }
    return false
  }

  private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
    if (node == null) return false
    val now = System.currentTimeMillis()
    if (now - lastClickAt < 450L) return false

    var current: AccessibilityNodeInfo? = node
    while (current != null) {
      if (current.isClickable) {
        if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
          lastClickAt = now
          return true
        }
      }
      current = current.parent
    }

    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    if (bounds.isEmpty) return false
    val path = Path()
    path.moveTo(bounds.exactCenterX(), bounds.exactCenterY())
    val stroke = GestureDescription.StrokeDescription(path, 0, 60)
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
