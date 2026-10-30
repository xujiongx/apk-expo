package expo.modules.autoassist

import android.content.Context
import android.content.Intent
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
    }

    AsyncFunction("isEnabled") {
      isAccessibilityServiceEnabled()
    }

    AsyncFunction("openSettings") {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
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
