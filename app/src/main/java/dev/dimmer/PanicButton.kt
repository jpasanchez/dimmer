package dev.dimmer

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Escape hatch. A small tappable window ABOVE the scrim that turns it off, so a
 * near-opaque screen never needs adb to recover from.
 *
 *  - Added AFTER the scrim: same window type from the same service z-orders by
 *    insertion, so later means on top.
 *  - Does NOT set FLAG_NOT_TOUCHABLE. The scrim stays pass-through; only this
 *    button's own bounds capture touches.
 *
 * Visibility is decided by VisibilityMonitor, not by scrim alpha alone, so
 * turning the panel brightness down while dimming also summons it.
 */
object PanicButton {

    /** Scrim alpha to fall back to after a tap. Must stay readable. */
    var safeAlpha: Float = 0.90f

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun sync() {
        val svc = DimmerAccessibilityService.instance ?: return hideInternal()
        val needed = DimmerOverlay.isShowing && VisibilityMonitor.isUnreadable(svc)
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
            text = "\u2715"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.85f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(0, 200, 83))
            }
            setOnClickListener {
                // Tapping hides the scrim, so isShowing goes false and sync()
                // removes this button -- no chance of a reappear loop.
                DimmerOverlay.setAlpha(safeAlpha)
                DimmerOverlay.hide(svc)
                DimmerTile.refresh(svc)
            }
        }

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
