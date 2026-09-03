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
 * Added as TYPE_ACCESSIBILITY_OVERLAY, which is why it must be created from
 * the AccessibilityService context -- WindowManager rejects that window type
 * from anyone else.
 */
object DimmerOverlay {

    // 0.95 rather than 1.0 on purpose: leaves a faint ghost so you can still
    // find your way to the tile. Native Android sits at roughly this value.
    var scrimColor: Int = Color.BLACK
    var scrimAlpha: Float = 0.95f
    var fadeMs: Long = 220L

    private val easing = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(svc: AccessibilityService) {
        if (view != null) return
        val wm = svc.getSystemService(WindowManager::class.java)
        val v = View(svc).apply {
            setBackgroundColor(scrimColor)
            alpha = 0f
        }
        wm.addView(v, buildParams())
        view = v
        v.animate().alpha(scrimAlpha).setDuration(fadeMs).setInterpolator(easing).start()
    }

    fun hide(svc: AccessibilityService) {
        val v = view ?: return
        view = null
        val wm = svc.getSystemService(WindowManager::class.java)
        v.animate().alpha(0f).setDuration(fadeMs).setInterpolator(easing)
            .withEndAction { runCatching { wm.removeViewImmediate(v) } }
            .start()
    }

    fun setAlpha(a: Float) {
        scrimAlpha = a.coerceIn(0f, 1f)
        view?.let {
            it.animate().cancel()
            it.alpha = scrimAlpha
        }
    }

    fun setColor(c: Int) {
        scrimColor = c
        view?.setBackgroundColor(c)
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
