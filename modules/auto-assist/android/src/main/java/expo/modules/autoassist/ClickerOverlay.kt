package expo.modules.autoassist

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统悬浮窗：选点层 + 可拖拽控制卡片（Material 风格）。
 */
object ClickerOverlay {
  // 与 App theme 对齐
  private const val C_SURFACE = 0xFFFFFFFF.toInt()
  private const val C_SURFACE_HIGH = 0xFFEEF2F6.toInt()
  private const val C_ON_SURFACE = 0xFF0F172A.toInt()
  private const val C_MUTED = 0xFF64748B.toInt()
  private const val C_PRIMARY = 0xFF0B57D0.toInt()
  private const val C_PRIMARY_CONT = 0xFFD3E3FD.toInt()
  private const val C_ON_PRIMARY_CONT = 0xFF041E49.toInt()
  private const val C_OUTLINE = 0xFFE2E8F0.toInt()
  private const val C_SUCCESS = 0xFF146C2E.toInt()
  private const val C_SUCCESS_SOFT = 0xFFC4EED0.toInt()
  private const val C_WARNING = 0xFFB54708.toInt()
  private const val C_WARNING_SOFT = 0xFFFFECB3.toInt()
  private const val C_ERROR_SOFT = 0xFFF9DEDC.toInt()
  private const val C_ERROR = 0xFFB3261E.toInt()

  private var appContext: Context? = null
  private var windowManager: WindowManager? = null

  private var panelView: LinearLayout? = null
  private var panelParams: WindowManager.LayoutParams? = null
  private var pickLayer: FrameLayout? = null
  private var schemePanel: FrameLayout? = null
  private var statusBadge: TextView? = null
  private var statusDetail: TextView? = null
  private var primaryBtn: TextView? = null
  private var intervalInput: EditText? = null
  private var activeSchemeId: String? = null

  private data class MarkerWindow(
    val view: TextView,
    val params: WindowManager.LayoutParams,
    val index: Int,
  )

  private val markerWindows = mutableListOf<MarkerWindow>()
  private var rebuildingMarkers = false

  private var dragDx = 0f
  private var dragDy = 0f
  private val handlerMain = android.os.Handler(android.os.Looper.getMainLooper())

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

    panelView = buildPanel(ctx)
    panelParams = WindowManager.LayoutParams(
      dp(ctx, 312),
      WindowManager.LayoutParams.WRAP_CONTENT,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = dp(ctx, 20)
      y = dp(ctx, 140)
    }
    windowManager?.addView(panelView, panelParams)
    refreshControls()
    redrawMarkers()
  }

  fun hide() {
    ClickerEngine.stop()
    ClickerEngine.onStateChanged = null
    removePickLayer()
    hideSchemePanel()
    clearMarkerWindows()
    panelView?.let { safeRemove(it) }
    panelView = null
    panelParams = null
    statusBadge = null
    statusDetail = null
    primaryBtn = null
    intervalInput = null
    activeSchemeId = null
    ClickerEngine.resetAll()
  }

  private fun buildPanel(ctx: Context): LinearLayout {
    val root = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 14))
      background = cardBg(ctx)
      elevation = dp(ctx, 10).toFloat()
      clipToOutline = true
    }

    // 顶栏：拖动手柄 + 关闭
    val top = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    val handleCol = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setOnTouchListener(dragListener())
      isClickable = true
    }
    handleCol.addView(View(ctx).apply {
      layoutParams = LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(ctx, 8)
      }
      background = roundBg(0xFFCBD5E1.toInt(), dp(ctx, 2).toFloat())
    })
    handleCol.addView(TextView(ctx).apply {
      text = "自动点击"
      setTextColor(C_ON_SURFACE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    })
    top.addView(handleCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

    val close = TextView(ctx).apply {
      text = "✕"
      gravity = Gravity.CENTER
      setTextColor(C_MUTED)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      background = roundBg(C_SURFACE_HIGH, dp(ctx, 16).toFloat())
      layoutParams = LinearLayout.LayoutParams(dp(ctx, 32), dp(ctx, 32))
      setOnClickListener { hide() }
    }
    top.addView(close)
    root.addView(top)

    // 状态徽章 + 说明
    statusBadge = TextView(ctx).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(ctx, 10) }
    }
    root.addView(statusBadge)

    statusDetail = TextView(ctx).apply {
      setTextColor(C_MUTED)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setPadding(0, dp(ctx, 4), 0, 0)
    }
    root.addView(statusDetail)

    // 间隔输入
    val intervalCard = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
      background = outlinedFieldBg(ctx)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(ctx, 12) }
    }
    intervalCard.addView(TextView(ctx).apply {
      text = "点击间隔"
      setTextColor(C_MUTED)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    })
    intervalInput = EditText(ctx).apply {
      setText(ClickerEngine.intervalMs.toString())
      setTextColor(C_ON_SURFACE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      inputType = android.text.InputType.TYPE_CLASS_NUMBER
      background = null
      gravity = Gravity.END or Gravity.CENTER_VERTICAL
      setPadding(dp(ctx, 8), 0, dp(ctx, 4), 0)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
    intervalCard.addView(intervalInput)
    intervalCard.addView(TextView(ctx).apply {
      text = "ms"
      setTextColor(C_MUTED)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    })
    root.addView(intervalCard)

    // 方案
    val schemeRow = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(ctx, 12) }
    }
    schemeRow.addView(
      tonalBtn(ctx, "保存方案", C_PRIMARY_CONT, C_ON_PRIMARY_CONT) { promptSaveScheme() },
      flexLp(ctx, true)
    )
    schemeRow.addView(
      tonalBtn(ctx, "我的方案", C_SURFACE_HIGH, C_ON_SURFACE) { showSchemePanel() },
      flexLp(ctx, false)
    )
    root.addView(schemeRow)

    // 次要操作
    val row = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(ctx, 8) }
    }
    row.addView(tonalBtn(ctx, "选点", C_PRIMARY_CONT, C_ON_PRIMARY_CONT) { startPicking() }, flexLp(ctx, true))
    row.addView(tonalBtn(ctx, "清除", C_SURFACE_HIGH, C_MUTED) {
      ClickerEngine.clearPoints()
      activeSchemeId = null
      redrawMarkers()
      toast("已清除点位")
    }, flexLp(ctx, true))
    row.addView(tonalBtn(ctx, "结束", C_ERROR_SOFT, C_ERROR) {
      ClickerEngine.stop()
      toast("已停止点击")
    }, flexLp(ctx, false))
    root.addView(row)

    // 主按钮
    primaryBtn = TextView(ctx).apply {
      gravity = Gravity.CENTER
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      setPadding(0, dp(ctx, 14), 0, dp(ctx, 14))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(ctx, 10) }
      setOnClickListener { toggleRun() }
    }
    root.addView(primaryBtn)

    root.addView(TextView(ctx).apply {
      text = "按住标题上方横条可拖动"
      gravity = Gravity.CENTER
      setTextColor(0xFF94A3B8.toInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
      setPadding(0, dp(ctx, 8), 0, 0)
    })

    return root
  }

  private fun flexLp(ctx: Context, withGap: Boolean) =
    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
      if (withGap) marginEnd = dp(ctx, 8)
    }

  private fun promptSaveScheme() {
    val ctx = appContext ?: return
    if (ClickerEngine.state == ClickerEngine.State.RUNNING) {
      toast("请先暂停或结束再保存")
      return
    }
    applyIntervalFromInput()
    val points = ClickerEngine.snapshotPoints()
    if (points.isEmpty()) {
      toast("请先选点再保存方案")
      return
    }

    val input = EditText(ctx).apply {
      setText(defaultSchemeName(ctx))
      setTextColor(C_ON_SURFACE)
      setHintTextColor(C_MUTED)
      hint = "方案名称"
      inputType = InputType.TYPE_CLASS_TEXT
      setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
      setSelectAllOnFocus(true)
    }

    val builder = AlertDialog.Builder(ctx)
      .setTitle(if (activeSchemeId != null) "保存 / 另存方案" else "保存选点方案")
      .setMessage("将保存 ${points.size} 个点位与间隔 ${ClickerEngine.intervalMs} ms")
      .setView(input)
      .setPositiveButton("保存") { _, _ ->
        val name = input.text?.toString()?.trim().orEmpty()
        val scheme = ClickerSchemeStore.save(
          context = ctx,
          name = name,
          points = points,
          intervalMs = ClickerEngine.intervalMs,
          replaceId = null,
        )
        activeSchemeId = scheme.id
        enablePanelFocusTemporarily(false)
        toast("已保存「${scheme.name}」")
      }
      .setNegativeButton("取消") { _, _ ->
        enablePanelFocusTemporarily(false)
      }
    val currentId = activeSchemeId
    if (currentId != null) {
      builder.setNeutralButton("覆盖当前") { _, _ ->
        val name = input.text?.toString()?.trim().orEmpty()
        val scheme = ClickerSchemeStore.save(
          context = ctx,
          name = name,
          points = points,
          intervalMs = ClickerEngine.intervalMs,
          replaceId = currentId,
        )
        activeSchemeId = scheme.id
        enablePanelFocusTemporarily(false)
        toast("已覆盖「${scheme.name}」")
      }
    }
    val dialog = builder.create()
    dialog.setOnDismissListener { enablePanelFocusTemporarily(false) }
    dialog.window?.setType(overlayType())
    dialog.show()
    enablePanelFocusTemporarily(true)
    input.requestFocus()
  }

  private fun defaultSchemeName(ctx: Context): String {
    val n = ClickerSchemeStore.list(ctx).size + 1
    val time = SimpleDateFormat("MMdd-HHmm", Locale.getDefault()).format(Date())
    return "方案$n · $time"
  }

  private fun showSchemePanel() {
    val ctx = appContext ?: return
    hideSchemePanel()

    val root = FrameLayout(ctx).apply {
      setBackgroundColor(0x66121A2A)
      setOnClickListener { hideSchemePanel() }
    }

    val sheet = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 20))
      background = sheetBg(ctx)
      elevation = dp(ctx, 12).toFloat()
      setOnClickListener { /* 阻止点穿关闭 */ }
    }
    sheet.addView(View(ctx).apply {
      layoutParams = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 4)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(ctx, 12)
      }
      background = roundBg(0xFFCBD5E1.toInt(), dp(ctx, 2).toFloat())
    })

    val titleRow = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    titleRow.addView(TextView(ctx).apply {
      text = "我的方案"
      setTextColor(C_ON_SURFACE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    titleRow.addView(TextView(ctx).apply {
      text = "关闭"
      setTextColor(C_PRIMARY)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setPadding(dp(ctx, 8), dp(ctx, 4), 0, dp(ctx, 4))
      setOnClickListener { hideSchemePanel() }
    })
    sheet.addView(titleRow)

    val listHost = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(ctx, 8) }
    }

    val schemes = ClickerSchemeStore.list(ctx)
    if (schemes.isEmpty()) {
      listHost.addView(TextView(ctx).apply {
        text = "还没有保存的方案。\n选好点后点「保存方案」。"
        setTextColor(C_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(ctx, 16), 0, dp(ctx, 8))
        gravity = Gravity.CENTER
      })
    } else {
      val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
      schemes.forEach { scheme ->
        listHost.addView(buildSchemeRow(ctx, scheme, fmt))
      }
    }

    val scroller = ScrollView(ctx).apply {
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(ctx, 320)
      )
      addView(listHost)
    }
    sheet.addView(scroller)

    root.addView(
      sheet,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM
      )
    )
    schemePanel = root
    attachFullScreen(root, focusable = true)
    bringPanelToFront(windowManager ?: return)
  }

  private fun buildSchemeRow(
    ctx: Context,
    scheme: ClickerSchemeStore.Scheme,
    fmt: SimpleDateFormat,
  ): LinearLayout {
    val row = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
      background = outlinedFieldBg(ctx)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { bottomMargin = dp(ctx, 8) }
    }
    val active = scheme.id == activeSchemeId
    row.addView(TextView(ctx).apply {
      text = if (active) "● ${scheme.name}" else scheme.name
      setTextColor(if (active) C_PRIMARY else C_ON_SURFACE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    })
    row.addView(TextView(ctx).apply {
      text = "${scheme.points.size} 个点 · ${scheme.intervalMs} ms · ${fmt.format(Date(scheme.updatedAt))}"
      setTextColor(C_MUTED)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setPadding(0, dp(ctx, 2), 0, dp(ctx, 8))
    })
    val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    actions.addView(
      tonalBtn(ctx, "加载", C_PRIMARY, Color.WHITE) {
        loadScheme(scheme)
      },
      flexLp(ctx, true)
    )
    actions.addView(
      tonalBtn(ctx, "删除", C_ERROR_SOFT, C_ERROR) {
        confirmDeleteScheme(scheme)
      },
      flexLp(ctx, false)
    )
    row.addView(actions)
    return row
  }

  private fun loadScheme(scheme: ClickerSchemeStore.Scheme) {
    if (ClickerEngine.state == ClickerEngine.State.RUNNING) {
      toast("请先暂停或结束再加载")
      return
    }
    removePickLayer()
    val err = ClickerEngine.loadScheme(scheme.points, scheme.intervalMs)
    if (err != null) {
      toast(err)
      return
    }
    activeSchemeId = scheme.id
    intervalInput?.setText(ClickerEngine.intervalMs.toString())
    hideSchemePanel()
    redrawMarkers()
    refreshControls()
    toast("已加载「${scheme.name}」")
  }

  private fun confirmDeleteScheme(scheme: ClickerSchemeStore.Scheme) {
    val ctx = appContext ?: return
    val dialog = AlertDialog.Builder(ctx)
      .setTitle("删除方案")
      .setMessage("确定删除「${scheme.name}」？")
      .setPositiveButton("删除") { _, _ ->
        ClickerSchemeStore.delete(ctx, scheme.id)
        if (activeSchemeId == scheme.id) activeSchemeId = null
        toast("已删除")
        hideSchemePanel()
        showSchemePanel()
      }
      .setNegativeButton("取消", null)
      .create()
    dialog.window?.setType(overlayType())
    dialog.show()
  }

  private fun hideSchemePanel() {
    schemePanel?.let { safeRemove(it) }
    schemePanel = null
  }

  private fun enablePanelFocusTemporarily(enable: Boolean) {
    panelParams?.let { lp ->
      if (enable) {
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
      } else {
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      }
      try {
        windowManager?.updateViewLayout(panelView, lp)
      } catch (_: Exception) {
      }
    }
  }

  private fun startPicking() {
    if (ClickerEngine.state == ClickerEngine.State.RUNNING) {
      toast("请先暂停或结束")
      return
    }
    applyIntervalFromInput()
    ClickerEngine.enterPicking()
    showPickLayer()
    toast("点击屏幕添加点位")
  }

  private fun showPickLayer() {
    val ctx = appContext ?: return
    if (pickLayer != null) return
    pickLayer = FrameLayout(ctx).apply {
      setBackgroundColor(0x59121A2A)
      setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
          val ok = ClickerEngine.addPoint(event.rawX, event.rawY)
          if (ok) redrawMarkers()
          else toast("点位已满（最多 20 个）")
        }
        true
      }
    }

    val sheet = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 20))
      background = sheetBg(ctx)
      elevation = dp(ctx, 12).toFloat()
      setOnTouchListener { _, _ -> true }
    }
    sheet.addView(View(ctx).apply {
      layoutParams = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 4)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(ctx, 12)
      }
      background = roundBg(0xFFCBD5E1.toInt(), dp(ctx, 2).toFloat())
    })
    sheet.addView(TextView(ctx).apply {
      text = "选点模式"
      setTextColor(C_ON_SURFACE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      gravity = Gravity.CENTER
    })
    sheet.addView(TextView(ctx).apply {
      text = "点屏幕添加（最多 20）· 长按圆点可拖动"
      setTextColor(C_MUTED)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      gravity = Gravity.CENTER
      setPadding(0, dp(ctx, 4), 0, dp(ctx, 14))
    })

    val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    actions.addView(
      tonalBtn(ctx, "撤销上一点", C_SURFACE_HIGH, C_ON_SURFACE) {
        ClickerEngine.undoPoint()
        redrawMarkers()
      },
      flexLp(ctx, true)
    )
    actions.addView(
      tonalBtn(ctx, "完成选点", C_PRIMARY, Color.WHITE) {
        applyIntervalFromInput()
        ClickerEngine.finishPicking()
        removePickLayer()
        if (ClickerEngine.pointCount() == 0) toast("未添加点位")
        else toast("已保存 ${ClickerEngine.pointCount()} 个点")
      },
      flexLp(ctx, false)
    )
    sheet.addView(actions)

    pickLayer!!.addView(
      sheet,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM
      )
    )
    attachFullScreen(pickLayer!!, focusable = true)
    // 圆点窗口盖在选点层之上，才能长按拖动；空白处仍由选点层接收点击
    redrawMarkers()
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
    val ctx = appContext ?: return
    val count = ClickerEngine.pointCount()
    val (badge, badgeBg, badgeFg, detail) = when (ClickerEngine.state) {
      ClickerEngine.State.PICKING ->
        Quad("选点中", C_PRIMARY_CONT, C_ON_PRIMARY_CONT, "点屏幕加点 · 长按圆点可拖动")
      ClickerEngine.State.RUNNING ->
        Quad("运行中", C_SUCCESS_SOFT, C_SUCCESS, "$count 个点 · ${ClickerEngine.intervalMs} ms（运行中不可拖点）")
      ClickerEngine.State.PAUSED ->
        Quad("已暂停", C_WARNING_SOFT, C_WARNING, "$count 个点 · 长按圆点可拖动调整")
      ClickerEngine.State.READY ->
        Quad("就绪", C_SUCCESS_SOFT, C_SUCCESS, "$count 个点 · 长按圆点可拖动 · 可开启")
      ClickerEngine.State.IDLE ->
        Quad("待选点", C_SURFACE_HIGH, C_MUTED, "先点「选点」，在目标界面标定位置")
    }
    statusBadge?.apply {
      text = badge
      setTextColor(badgeFg)
      background = roundBg(badgeBg, dp(ctx, 20).toFloat())
    }
    statusDetail?.text = detail

    primaryBtn?.apply {
      when (ClickerEngine.state) {
        ClickerEngine.State.RUNNING -> {
          text = "暂停"
          setTextColor(C_WARNING)
          background = roundBg(C_WARNING_SOFT, dp(ctx, 24).toFloat())
        }
        ClickerEngine.State.PAUSED -> {
          text = "继续"
          setTextColor(Color.WHITE)
          background = roundBg(C_PRIMARY, dp(ctx, 24).toFloat())
        }
        else -> {
          text = "开启自动点击"
          setTextColor(Color.WHITE)
          background = roundBg(C_PRIMARY, dp(ctx, 24).toFloat())
        }
      }
    }
  }

  private data class Quad(val a: String, val b: Int, val c: Int, val d: String)

  private fun clearMarkerWindows() {
    markerWindows.toList().forEach { safeRemove(it.view) }
    markerWindows.clear()
  }

  private fun redrawMarkers() {
    if (rebuildingMarkers) return
    val ctx = appContext ?: return
    val wm = windowManager ?: return
    rebuildingMarkers = true
    try {
      clearMarkerWindows()
      val size = dp(ctx, 36)
      val canDrag = ClickerEngine.state != ClickerEngine.State.RUNNING
      ClickerEngine.snapshotPoints().forEachIndexed { index, p ->
        val view = TextView(ctx).apply {
          text = "${index + 1}"
          gravity = Gravity.CENTER
          setTextColor(Color.WHITE)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
          background = markerBg(ctx)
          elevation = dp(ctx, 4).toFloat()
          alpha = if (canDrag) 1f else 0.82f
        }
        val lp = smallOverlayParams(ctx, size, size).apply {
          x = (p.x - size / 2f).toInt()
          y = (p.y - size / 2f).toInt()
          if (!canDrag) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
          }
        }
        if (canDrag) {
          bindMarkerDrag(view, lp, index, size)
        }
        wm.addView(view, lp)
        markerWindows.add(MarkerWindow(view, lp, index))
      }
      // 控制面板保持在最上层
      bringPanelToFront(wm)
    } finally {
      rebuildingMarkers = false
    }
  }

  private fun bringPanelToFront(wm: WindowManager) {
    val panel = panelView ?: return
    val params = panelParams ?: return
    try {
      wm.removeView(panel)
    } catch (_: Exception) {
    }
    try {
      wm.addView(panel, params)
    } catch (_: Exception) {
    }
  }

  private fun bindMarkerDrag(
    view: TextView,
    lp: WindowManager.LayoutParams,
    index: Int,
    size: Int,
  ) {
    val ctx = appContext ?: return
    val wm = windowManager ?: return
    val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    var dragging = false
    var downRawX = 0f
    var downRawY = 0f
    var grabOffsetX = 0f
    var grabOffsetY = 0f

    val armLongPress = Runnable {
      dragging = true
      view.animate().scaleX(1.18f).scaleY(1.18f).setDuration(80).start()
      toast("拖动调整第 ${index + 1} 点")
    }

    view.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = event.rawX
          downRawY = event.rawY
          grabOffsetX = event.rawX - (lp.x + size / 2f)
          grabOffsetY = event.rawY - (lp.y + size / 2f)
          dragging = false
          handlerMain.postDelayed(armLongPress, longPressTimeout)
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = event.rawX - downRawX
          val dy = event.rawY - downRawY
          if (!dragging && (dx * dx + dy * dy) > slop * slop) {
            handlerMain.removeCallbacks(armLongPress)
          }
          if (dragging) {
            val cx = event.rawX - grabOffsetX
            val cy = event.rawY - grabOffsetY
            lp.x = (cx - size / 2f).toInt()
            lp.y = (cy - size / 2f).toInt()
            try {
              wm.updateViewLayout(view, lp)
            } catch (_: Exception) {
            }
            ClickerEngine.movePoint(index, cx, cy)
          }
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          handlerMain.removeCallbacks(armLongPress)
          if (dragging) {
            val cx = lp.x + size / 2f
            val cy = lp.y + size / 2f
            ClickerEngine.movePoint(index, cx, cy)
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
          }
          dragging = false
          true
        }
        else -> false
      }
    }
  }

  private fun smallOverlayParams(ctx: Context, w: Int, h: Int): WindowManager.LayoutParams {
    return WindowManager.LayoutParams(
      w,
      h,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        fitInsetsTypes = 0
      }
    }
  }

  private fun applyIntervalFromInput() {
    val raw = intervalInput?.text?.toString()?.toLongOrNull() ?: return
    ClickerEngine.intervalMs = raw
  }

  private fun attachFullScreen(view: View, focusable: Boolean) {
    var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    if (!focusable) {
      flags = flags or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      overlayType(),
      flags,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = 0
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        fitInsetsTypes = 0
      }
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

  private fun tonalBtn(
    ctx: Context,
    label: String,
    bg: Int,
    fg: Int,
    onClick: () -> Unit
  ): TextView {
    return TextView(ctx).apply {
      text = label
      gravity = Gravity.CENTER
      setTextColor(fg)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      setPadding(dp(ctx, 6), dp(ctx, 12), dp(ctx, 6), dp(ctx, 12))
      background = roundBg(bg, dp(ctx, 22).toFloat())
      setOnClickListener { onClick() }
    }
  }

  private fun cardBg(ctx: Context): GradientDrawable {
    return GradientDrawable().apply {
      setColor(C_SURFACE)
      cornerRadius = dp(ctx, 20).toFloat()
      setStroke(dp(ctx, 1), C_OUTLINE)
    }
  }

  private fun sheetBg(ctx: Context): GradientDrawable {
    val r = dp(ctx, 22).toFloat()
    return GradientDrawable().apply {
      setColor(C_SURFACE)
      cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }
  }

  private fun outlinedFieldBg(ctx: Context): GradientDrawable {
    return GradientDrawable().apply {
      setColor(C_SURFACE_HIGH)
      cornerRadius = dp(ctx, 12).toFloat()
      setStroke(dp(ctx, 1), C_OUTLINE)
    }
  }

  private fun markerBg(ctx: Context): LayerDrawable {
    val ring = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.WHITE)
    }
    val core = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(C_PRIMARY)
    }
    val pad = dp(ctx, 2)
    return LayerDrawable(arrayOf(ring, core)).apply {
      setLayerInset(1, pad, pad, pad, pad)
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
    handlerMain.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
  }
}
