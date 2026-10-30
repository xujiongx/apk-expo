package expo.modules.autoassist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class AutoAssistModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("AutoAssist")

    Events("onProgress")

    OnCreate {
      AutoAssistService.onProgress = { step, message, ok ->
        this@AutoAssistModule.sendEvent(
          "onProgress",
          mapOf(
            "step" to step,
            "message" to message,
            "ok" to ok
          )
        )
      }
    }

    OnDestroy {
      AutoAssistService.onProgress = null
      AutoAssistService.stopTask()
      // 不要在桥销毁时强行 hide 浮窗：用户可能切到其他 App 使用点击器
    }

    AsyncFunction("isEnabled") {
      isAccessibilityServiceEnabled()
    }

    AsyncFunction("openSettings") {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }

    AsyncFunction("isIgnoringBatteryOptimizations") {
      val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
      pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    AsyncFunction("openBatteryOptimizationSettings") {
      try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
          data = Uri.parse("package:${context.packageName}")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
      } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
      }
    }

    AsyncFunction("canDrawOverlays") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
      } else {
        true
      }
    }

    AsyncFunction("openOverlaySettings") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      }
    }

    AsyncFunction("showClickerOverlay") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        throw Exception("请先开启「显示在其他应用上层」权限")
      }
      if (!isAccessibilityServiceEnabled()) {
        throw Exception("请先开启无障碍服务「工具箱自动签到」")
      }
      val activity = appContext.currentActivity
      val ctx = activity ?: context
      Handler(Looper.getMainLooper()).post {
        ClickerOverlay.show(ctx)
      }
      null
    }

    AsyncFunction("hideClickerOverlay") {
      Handler(Looper.getMainLooper()).post {
        ClickerOverlay.hide()
      }
      null
    }

    AsyncFunction("isClickerOverlayShowing") {
      ClickerOverlay.isShowing()
    }

    AsyncFunction("startKuaishouCheckIn") { packageName: String ->
      if (!isAccessibilityServiceEnabled()) {
        throw Exception("请先在系统设置中开启「工具箱自动签到」无障碍服务")
      }
      val pkg = if (packageName.isBlank()) "com.kuaishou.nebula" else packageName
      AutoAssistService.startTask(pkg)
    }

    AsyncFunction("stop") {
      AutoAssistService.stopTask()
      ClickerEngine.stop()
    }
  }

  private fun isAccessibilityServiceEnabled(): Boolean {
    val expected =
      context.packageName + "/" + AutoAssistService::class.java.canonicalName
    val enabled = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
      val item = splitter.next()
      if (item.equals(expected, ignoreCase = true)) {
        return true
      }
      if (item.contains(context.packageName) && item.contains("AutoAssistService")) {
        return true
      }
    }
    return false
  }
}
