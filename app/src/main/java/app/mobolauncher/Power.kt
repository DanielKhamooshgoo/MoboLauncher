package app.mobolauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Optional helper (declared only in the "pwr" build). A normal app cannot power the device off,
 * so once the user turns this service on in Accessibility settings, the launcher can ask Android
 * to show its own power menu. The service does not read the screen.
 */
class PowerService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        var instance: PowerService? = null
    }
}

object PowerHelper {
    /** True when this build contains the helper service. */
    fun declared(ctx: Context): Boolean {
        val i = Intent("android.accessibilityservice.AccessibilityService").setPackage(ctx.packageName)
        return ctx.packageManager.queryIntentServices(i, 0).isNotEmpty()
    }

    fun openAccessibilitySettings(ctx: Context): Boolean = try {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /** Returns null on success, or a short message to show the user. */
    fun openPowerMenu(ctx: Context): String? {
        val svc = PowerService.instance
        if (svc != null) {
            return if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) {
                null
            } else {
                "Android did not open its power menu."
            }
        }
        if (!declared(ctx)) return "This version of the app can't open the power menu."
        openAccessibilitySettings(ctx)
        return "Turn on “Mobo Launcher power menu” in Accessibility, then try again."
    }
}
