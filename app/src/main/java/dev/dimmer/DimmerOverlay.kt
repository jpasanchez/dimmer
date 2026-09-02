package dev.dimmer

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator

/**
 * The scrim itself.
 *
 * Deliberately one plain [View] with an animated alpha, because that is what
 * SystemUI does -- see AOSP's ScrimController and the scrim_behind view in
 * super_status_bar.xml. Everything else here is window plumbing to float that
 * view above other apps.
 */
object DimmerOverlay {

    // ---- Tunables. Placeholders until measured. See README.md for the
    // ---- two-screenshot procedure that solves for the real values.
    var scrimColor: Int = Color.BLACK      // AOSP tints this from the Monet palette
    var scrimAlpha: Float = 0.55f
    var blurRadiusPx: Int = 60             // 0 disables blur
    var fadeMs: Long = 220L

    /** Material standard easing; close to what the shade uses. */
    private val easing = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(ctx: Context) {
        if (view != null) return
        val wm = ctx.getSystemService(WindowManager::class.java)
        val v = View(ctx).apply {
            setBackgroundColor(scrimColor)
            alpha = 0f
        }
        wm.addView(v, buildParams(wm))
        view = v
        v.animate().alpha(scrimAlpha).setDuration(fadeMs).setInterpolator(easing).start()
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        view = null
        val wm = ctx.getSystemService(WindowManager::class.java)
        v.animate().alpha(0f).setDuration(fadeMs).setInterpolator(easing)
            .withEndAction { runCatching { wm.removeViewImmediate(v) } }
            .start()
    }

    /** Live-adjust while the scrim is up, for dialling in fidelity. */
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

    private fun buildParams(wm: WindowManager): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_TOUCHABLE is the critical one: touches fall straight through
            // to whatever app is underneath.
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        // Extend geometry over the bars and cutout rather than being inset out
        // of them. Note SystemUI's own bar content still draws ABOVE this
        // window -- the clock and gesture pill will not be dimmed.
        lp.fitInsetsTypes = 0
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        // Cross-window blur. Returns false under battery saver or where the
        // compositor can't afford it, so always check rather than assume.
        if (blurRadiusPx > 0 && wm.isCrossWindowBlurEnabled) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = blurRadiusPx
        }
        return lp
    }
}
