package dev.dimmer

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Escape hatch. A small tappable window that sits ABOVE the scrim and turns it
 * off, so a near-opaque screen never requires adb to recover from.
 *
 * Two details make this work:
 *  - It is added AFTER the scrim. Windows of the same type from the same
 *    service z-order by insertion, so later means on top.
 *  - It does NOT set FLAG_NOT_TOUCHABLE. The scrim stays pass-through; only
 *    this button's own bounds capture touches.
 */
object PanicButton {

    /** Above this scrim alpha, navigating by sight is unreliable. */
    const val THRESHOLD = 0.97  f

    /** Scrim alpha to fall back to when the button is tapped. */
    const val SAFE_ALPHA = 0.80f

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    /** Add or remove the button to match the current scrim state. */
    fun sync() {
        val svc = DimmerAccessibilityService.instance ?: return hideInternal()
        val needed = DimmerOverlay.isShowing && DimmerOverlay.scrimAlpha >= THRESHOLD
        when {
            needed && view == null -> add(svc)
            !needed && view != null -> hideInternal()
        }
    }

    /** Re-add above a freshly created scrim so it keeps the top slot. */
    fun raise() {
        if (view == null) return
        hideInternal()
        sync()
    }

    fun hide() = hideInternal()

    private fun add(svc: DimmerAccessibilityService) {
        val d = svc.resources.displayMetrics.density
        val size = (60 * d).toInt()
        val margin = (28 * d).toInt()

        val btn = TextView(svc).apply {
            text = "\u2715"                     // multiplication X
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.85f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(0, 200, 83))
            }
            setOnClickListener {
                DimmerOverlay.setAlpha(SAFE_ALPHA)
                DimmerOverlay.hide(svc)
                DimmerTile.refresh(svc)
            }
        }

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Deliberately NOT touchable-flagged: this one must receive taps.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = margin
            y = margin
        }

        runCatching {
            svc.getSystemService(WindowManager::class.java).addView(btn, lp)
            view = btn
        }
    }

    private fun hideInternal() {
        val v = view ?: return
        view = null
        val svc = DimmerAccessibilityService.instance ?: return
        runCatching {
            svc.getSystemService(WindowManager::class.java).removeViewImmediate(v)
        }
    }
}
