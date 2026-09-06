package dev.dimmer

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator

/**
 * The scrim: one plain View with an animated alpha.
 *
 * TYPE_ACCESSIBILITY_OVERLAY, so it must be created from the
 * AccessibilityService context -- WindowManager rejects that type from anyone
 * else, and TYPE_APPLICATION_OVERLAY would be capped at 0.8 opacity.
 */
object DimmerOverlay {

    var mode: ScrimPalette.Mode = ScrimPalette.Mode.SHADE_MATCH
    var scrimColor: Int = ScrimPalette.MEASURED_SHADE
    var scrimAlpha: Float = 0.95f
    var fadeMs: Long = 220L

    /** Fires on show, hide, alpha or colour change -- including from the
     *  panic button and the QS tile, which act behind the UI's back. */
    var onStateChanged: (() -> Unit)? = null

    private val easing = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(svc: AccessibilityService) {
        if (view != null) return
        // Re-resolve every time, so SYSTEM mode tracks wallpaper changes
        // without needing to restart anything.
        scrimColor = ScrimPalette.resolve(svc, mode)

        val wm = svc.getSystemService(WindowManager::class.java)
        val v = View(svc).apply {
            setBackgroundColor(scrimColor)
            alpha = 0f
        }
        wm.addView(v, buildParams())
        view = v
        v.animate().alpha(scrimAlpha).setDuration(fadeMs).setInterpolator(easing).start()

        PanicButton.raise()
        PanicButton.sync()
        onStateChanged?.invoke()
    }

    fun hide(svc: AccessibilityService) {
        PanicButton.hide()
        val v = view ?: return
        view = null
        val wm = svc.getSystemService(WindowManager::class.java)
        v.animate().alpha(0f).setDuration(fadeMs).setInterpolator(easing)
            .withEndAction { runCatching { wm.removeViewImmediate(v) } }
            .start()
        onStateChanged?.invoke()
    }

    fun setAlpha(a: Float) {
        scrimAlpha = a.coerceIn(0f, 1f)
        view?.let {
            it.animate().cancel()
            it.alpha = scrimAlpha
        }
        PanicButton.sync()
        onStateChanged?.invoke()
    }

    /** Switch source and apply immediately, even mid-dim. */
    fun setMode(ctx: android.content.Context, m: ScrimPalette.Mode) {
        mode = m
        scrimColor = ScrimPalette.resolve(ctx, m)
        view?.setBackgroundColor(scrimColor)
        onStateChanged?.invoke()
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.fitInsetsTypes = 0
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        return lp
    }
}
