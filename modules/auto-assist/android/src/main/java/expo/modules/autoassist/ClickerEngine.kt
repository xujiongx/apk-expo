package expo.modules.autoassist

import android.os.Handler
import android.os.Looper

/**
 * 自动点击器状态机：点位、间隔、启停。
 * 真正点击走 [AutoAssistService] 手势；浮窗 UI 走 [ClickerOverlay]。
 */
object ClickerEngine {
  enum class State {
    IDLE, PICKING, READY, RUNNING, PAUSED
  }

  data class Point(val x: Float, val y: Float)

  @Volatile
  var state: State = State.IDLE
    private set

  var intervalMs: Long = 800L
    set(value) {
      field = value.coerceIn(100L, 10_000L)
    }

  private val points = mutableListOf<Point>()
  private var cursor = 0
  private val handler = Handler(Looper.getMainLooper())

  var onStateChanged: ((State, Int) -> Unit)? = null

  fun pointCount(): Int = points.size

  fun snapshotPoints(): List<Point> = points.toList()

  fun enterPicking() {
    if (state == State.RUNNING) stopLoop()
    state = State.PICKING
    notifyState()
  }

  fun finishPicking() {
    state = if (points.isEmpty()) State.IDLE else State.READY
    notifyState()
  }

  fun addPoint(x: Float, y: Float): Boolean {
    if (state != State.PICKING) return false
    if (points.size >= 20) return false
    points.add(Point(x, y))
    notifyState()
    return true
  }

  fun undoPoint(): Point? {
    if (points.isEmpty()) return null
    val removed = points.removeAt(points.lastIndex)
    if (state == State.READY && points.isEmpty()) {
      state = State.IDLE
    }
    notifyState()
    return removed
  }

  fun clearPoints() {
    stopLoop()
    points.clear()
    cursor = 0
    state = State.IDLE
    notifyState()
  }

  fun start(): String? {
    if (points.isEmpty()) return "请先选至少一个点击点位"
    if (AutoAssistService.get() == null) {
      return "无障碍未连接，请在系统设置中开启「工具箱自动签到」"
    }
    // 与签到任务互斥
    AutoAssistService.stopTask()
    state = State.RUNNING
    cursor = 0
    notifyState()
    handler.removeCallbacks(tickRunnable)
    handler.post(tickRunnable)
    return null
  }

  fun pause() {
    if (state != State.RUNNING) return
    state = State.PAUSED
    handler.removeCallbacks(tickRunnable)
    notifyState()
  }

  fun resume(): String? {
    if (state != State.PAUSED) return "当前不在暂停状态"
    if (AutoAssistService.get() == null) return "无障碍未连接"
    state = State.RUNNING
    notifyState()
    handler.removeCallbacks(tickRunnable)
    handler.post(tickRunnable)
    return null
  }

  fun stop() {
    stopLoop()
    state = if (points.isEmpty()) State.IDLE else State.READY
    notifyState()
  }

  fun resetAll() {
    stopLoop()
    points.clear()
    cursor = 0
    state = State.IDLE
    notifyState()
  }

  private fun stopLoop() {
    handler.removeCallbacks(tickRunnable)
    if (state == State.RUNNING || state == State.PAUSED) {
      // caller sets state
    }
  }

  private val tickRunnable = object : Runnable {
    override fun run() {
      if (state != State.RUNNING) return
      val list = points.toList()
      if (list.isEmpty()) {
        stop()
        return
      }
      val p = list[cursor % list.size]
      val ok = AutoAssistService.performTap(p.x, p.y)
      if (!ok) {
        // 手势被拒时稍后再试，不中断整轮
      }
      cursor = (cursor + 1) % list.size
      handler.postDelayed(this, intervalMs)
    }
  }

  private fun notifyState() {
    onStateChanged?.invoke(state, points.size)
  }
}
