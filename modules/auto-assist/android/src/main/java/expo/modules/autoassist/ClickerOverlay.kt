package expo.modules.autoassist

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 系统悬浮窗：选点层 + 可拖拽控制条。
 */
object ClickerOverlay {
  private var appContext: Context? = null
  private var windowManager: WindowManager? = null

  private var panelView: LinearLayout? = null
  private var panelParams: WindowManager.LayoutParams? = null
  private var pickLayer: FrameLayout? = null
  private var markersLayer: FrameLayout? = null
  private var statusText: TextView? = null
  private var primaryBtn: TextView? = null
  private var intervalInput: EditText? = null

  fun isShowing(): Boolean = panelView != null

  fun show(context: Context) {
    if (panelView != null) return
    val ctx = context.applicationContext
    appContext = ctx
    windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    ClickerEngine.onStateChanged = { _, _ ->
      refreshControls()
      redrawMarkers()
    }

    markersLayer = FrameLayout(ctx).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }
    attachFullScreen(markersLayer!!, focusable = false)

    panelView = buildPanel(ctx)
    panelParams = WindowManager.LayoutParams(
      dp(ctx, 300),
      WindowManager.LayoutParams.WRAP_CONTENT,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = dp(ctx, 16)
      y = dp(ctx, 120)
    }
    windowManager?.addView(panelView, panelParams)
    refreshControls()
  }

  fun hide() {
    ClickerEngine.stop()
    ClickerEngine.onStateChanged = null
    removePickLayer()
    markersLayer?.let { safeRemove(it) }
    markersLayer = null
    panelView?.let { safeRemove(it) }
    panelView = null
    panelParams = null
    statusText = null
    primaryBtn = null
    intervalInput = null
    ClickerEngine.resetAll()
  }

  private fun buildPanel(ctx: Context): LinearLayout {
    val root = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
      background = roundBg(0xF0FFFFFF.toInt(), dp(ctx, 14).toFloat())
      elevation = dp(ctx, 8).toFloat()
    }

    val titleRow = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    val dragHandle = TextView(ctx).apply {
      text = "⋮⋮ 自动点击"
      setTextColor(0xFF0F172A.toInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setPadding(0, 0, dp(ctx, 8), 0)
      setOnTouchListener(dragListener())
    }
    val close = actionChip(ctx, "✕") {
      hide()
    }
    titleRow.addView(dragHandle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    titleRow.addView(close)
    root.addView(titleRow)

    statusText = TextView(ctx).apply {
      setTextColor(0xFF64748B.toInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setPadding(0, dp(ctx, 6), 0, dp(ctx, 6))
    }
    root.addView(statusText)

    val intervalRow = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    intervalRow.addView(TextView(ctx).apply {
      text = "间隔(ms)"
      setTextColor(0xFF64748B.toInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    })
    intervalInput = EditText(ctx).apply {
      setText(ClickerEngine.intervalMs.toString())
      setTextColor(0xFF0F172A.toInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      inputType = android.text.InputType.TYPE_CLASS_NUMBER
      setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4))
      layoutParams = LinearLayout.LayoutParams(dp(ctx, 88), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginStart = dp(ctx, 8)
      }
      // 需要输入时临时允许焦点
      setOnFocusChangeListener { _, hasFocus ->
        panelParams?.let { lp ->
          if (hasFocus) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
          } else {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            applyIntervalFromInput()
          }
          windowManager?.updateViewLayout(panelView, lp)
        }
      }
    }
    intervalRow.addView(intervalInput)
    root.addView(intervalRow)

    val btnRow1 = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(0, dp(ctx, 8), 0, 0)
    }
    btnRow1.addView(flexChip(ctx, "选点") { startPicking() }, chipLp(ctx))
    btnRow1.addView(flexChip(ctx, "清除") {
      ClickerEngine.clearPoints()
      redrawMarkers()
      toast("已清除点位")
    }, chipLp(ctx))
    btnRow1.addView(flexChip(ctx, "结束") {
      ClickerEngine.stop()
      toast("已停止点击")
    }, chipLp(ctx))
    root.addView(btnRow1)

    val btnRow2 = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(0, dp(ctx, 6), 0, 0)
    }
    primaryBtn = flexChip(ctx, "开启") { toggleRun() }.also {
      it.setBackgroundColor(0xFF0B57D0.toInt())
      it.setTextColor(Color.WHITE)
    }
    btnRow2.addView(primaryBtn, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ))
    root.addView(btnRow2)

    return root
  }

  private fun chipLp(ctx: Context) =
    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
      marginEnd = dp(ctx, 6)
    }

  private fun startPicking() {
    if (ClickerEngine.state == ClickerEngine.State.RUNNING) {
      toast("请先暂停或结束")
      return
    }
    applyIntervalFromInput()
    ClickerEngine.enterPicking()
    showPickLayer()
    toast("点击屏幕添加点位，完成后点「完成选点」")
  }

  private fun showPickLayer() {
    val ctx = appContext ?: return
    if (pickLayer != null) return
    pickLayer = FrameLayout(ctx).apply {
      setBackgroundColor(0x660F172A)
      setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
          val ok = ClickerEngine.addPoint(event.rawX, event.rawY)
          if (ok) {
            redrawMarkers()
          } else {
            toast("点位已满（最多 20 个）")
          }
        }
        true
      }
    }
    val bar = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
      setBackgroundColor(0xF0FFFFFF.toInt())
      gravity = Gravity.CENTER
    }
    bar.addView(flexChip(ctx, "撤销") {
      ClickerEngine.undoPoint()
      redrawMarkers()
    }, chipLp(ctx))
    bar.addView(flexChip(ctx, "完成选点") {
      applyIntervalFromInput()
      ClickerEngine.finishPicking()
      removePickLayer()
      if (ClickerEngine.pointCount() == 0) toast("未添加点位")
      else toast("已保存 ${ClickerEngine.pointCount()} 个点")
    }, chipLp(ctx))
    val barLp = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM
    )
    pickLayer!!.addView(bar, barLp)
    // 底部按钮区域不采集点：拦截 bar 触摸
    bar.setOnTouchListener { _, _ -> true }

    attachFullScreen(pickLayer!!, focusable = true)
  }

  private fun removePickLayer() {
    pickLayer?.let { safeRemove(it) }
    pickLayer = null
  }

  private fun toggleRun() {
    applyIntervalFromInput()
    when (ClickerEngine.state) {
      ClickerEngine.State.RUNNING -> {
        ClickerEngine.pause()
        toast("已暂停")
      }
      ClickerEngine.State.PAUSED -> {
        val err = ClickerEngine.resume()
        if (err != null) toast(err) else toast("继续点击")
      }
      else -> {
        val err = ClickerEngine.start()
        if (err != null) toast(err) else toast("已开启自动点击")
      }
    }
    refreshControls()
  }

  private fun refreshControls() {
    val count = ClickerEngine.pointCount()
    val label = when (ClickerEngine.state) {
      ClickerEngine.State.PICKING -> "选点中 · 已选 $count 个"
      ClickerEngine.State.RUNNING -> "运行中 · $count 个点 · ${ClickerEngine.intervalMs}ms"
      ClickerEngine.State.PAUSED -> "已暂停 · $count 个点"
      ClickerEngine.State.READY -> "就绪 · $count 个点"
      ClickerEngine.State.IDLE -> if (count == 0) "未选点 · 先点「选点」" else "就绪 · $count 个点"
    }
    statusText?.text = label
    primaryBtn?.text = when (ClickerEngine.state) {
      ClickerEngine.State.RUNNING -> "暂停"
      ClickerEngine.State.PAUSED -> "继续"
      else -> "开启"
    }
  }

  private fun redrawMarkers() {
    val layer = markersLayer ?: return
    val ctx = appContext ?: return
    layer.removeAllViews()
    ClickerEngine.snapshotPoints().forEachIndexed { index, p ->
      val size = dp(ctx, 28)
      val marker = TextView(ctx).apply {
        text = "${index + 1}"
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        background = roundBg(0xE00B57D0.toInt(), size / 2f)
      }
      val lp = FrameLayout.LayoutParams(size, size).apply {
        leftMargin = (p.x - size / 2f).toInt().coerceAtLeast(0)
        topMargin = (p.y - size / 2f).toInt().coerceAtLeast(0)
      }
      layer.addView(marker, lp)
    }
  }

  private fun applyIntervalFromInput() {
    val raw = intervalInput?.text?.toString()?.toLongOrNull() ?: return
    ClickerEngine.intervalMs = raw
  }

  private fun attachFullScreen(view: View, focusable: Boolean) {
    val flags = if (focusable) {
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    } else {
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      overlayType(),
      flags,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
    }
    // markers 不挡触摸；pick 层挡
    if (!focusable) {
      lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
    windowManager?.addView(view, lp)
  }

  private fun dragListener() = View.OnTouchListener { _, event ->
    val lp = panelParams ?: return@OnTouchListener false
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        dragDx = event.rawX - lp.x
        dragDy = event.rawY - lp.y
        true
      }
      MotionEvent.ACTION_MOVE -> {
        lp.x = (event.rawX - dragDx).toInt()
        lp.y = (event.rawY - dragDy).toInt()
        windowManager?.updateViewLayout(panelView, lp)
        true
      }
      else -> false
    }
  }

  private var dragDx = 0f
  private var dragDy = 0f

  private fun safeRemove(view: View) {
    try {
      windowManager?.removeView(view)
    } catch (_: Exception) {
    }
  }

  private fun overlayType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }
  }

  private fun actionChip(ctx: Context, label: String, onClick: () -> Unit): TextView {
    return TextView(ctx).apply {
      text = label
      setTextColor(0xFF0F172A.toInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
      setOnClickListener { onClick() }
    }
  }

  private fun flexChip(ctx: Context, label: String, onClick: () -> Unit): TextView {
    return TextView(ctx).apply {
      text = label
      gravity = Gravity.CENTER
      setTextColor(0xFF041E49.toInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setPadding(dp(ctx, 8), dp(ctx, 10), dp(ctx, 8), dp(ctx, 10))
      background = roundBg(0xFFD3E3FD.toInt(), dp(ctx, 20).toFloat())
      setOnClickListener { onClick() }
    }
  }

  private fun roundBg(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
  }

  private fun dp(ctx: Context, v: Int): Int =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      v.toFloat(),
      ctx.resources.displayMetrics
    ).toInt()

  private fun toast(msg: String) {
    val ctx = appContext ?: return
    HandlerMain.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
  }

  private val HandlerMain = android.os.Handler(android.os.Looper.getMainLooper())
}
