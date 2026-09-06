package dev.dimmer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Owner of the scrim.
 *
 * Exists for one reason: accessibility windows are TRUSTED, so they escape the
 * tapjacking opacity ceiling. A TYPE_APPLICATION_OVERLAY window with
 * FLAG_NOT_TOUCHABLE is clamped to
 * InputManager.getMaximumObscuringOpacityForTouch() -- 0.8 here, measured as
 * exactly 20% content throughput no matter what alpha was requested.
 *
 * Side benefit: the system keeps this bound, so no foreground service and no
 * ongoing notification.
 */
class DimmerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: DimmerAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null

        /** @return false if the service is not enabled yet. */
        fun toggle(): Boolean {
            val s = instance ?: return false
            if (DimmerOverlay.isShowing) DimmerOverlay.hide(s) else DimmerOverlay.show(s)
            DimmerTile.refresh(s)
            return true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        VisibilityMonitor.start(this)
        DimmerTile.refresh(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        VisibilityMonitor.stop(this)
        DimmerOverlay.hide(this)
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        VisibilityMonitor.stop(this)
        DimmerOverlay.hide(this)
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
