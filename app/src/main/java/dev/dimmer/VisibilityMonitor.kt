package dev.dimmer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Estimates whether the screen has become unreadable, so the panic button
 * appears for the right reason.
 *
 * Brightness and scrim alpha are NOT interchangeable, which an earlier version
 * of this got wrong:
 *
 *  - Low panel brightness scales everything uniformly. Contrast is untouched
 *    and the eye adapts, so content stays legible -- just dim.
 *  - Scrim alpha destroys contrast by pulling every pixel toward one colour.
 *    Adaptation cannot recover what is no longer there.
 *
 * So alpha dominates and brightness is a weak modifier, applied as a root
 * rather than a power.
 *
 * What this cannot see: other apps' overlays. No API exists, deliberately --
 * enumerating what covers the screen and how opaquely is the reconnaissance a
 * tapjacking attack would want. The only true composite is
 * AccessibilityService.takeScreenshot(), which is rate-limited, power-hungry,
 * and blind to backlight anyway.
 */
object VisibilityMonitor {

    /** Below this estimated visibility, show the escape hatch. */
    var floor: Float = 0.03f

    /**
     * Exponent on brightness. 0 ignores brightness entirely, 1 makes it as
     * important as alpha. Small values are correct here: a fourth root means
     * 5% brightness still counts as ~47% "readable", because it is.
     */
    var brightnessWeight: Float = 0.25f

    private var observer: ContentObserver? = null

    /** Panel brightness, 0..1. Falls back to 1.0 (fail safe: no false panic). */
    fun brightness(ctx: Context): Float {
        val cr = ctx.contentResolver
        runCatching { Settings.System.getFloat(cr, "screen_brightness_float") }
            .getOrNull()
            ?.let { if (it in 0f..1f) return it }
        runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) }
            .getOrNull()
            ?.let { return (it / 255f).coerceIn(0f, 1f) }
        return 1f
    }

    fun visibility(ctx: Context): Float {
        val b = brightness(ctx).coerceIn(0f, 1f)
        val weighted =
            if (brightnessWeight <= 0f) 1f
            else Math.pow(b.toDouble(), brightnessWeight.toDouble()).toFloat()
        return weighted * (1f - DimmerOverlay.scrimAlpha)
    }

    fun isUnreadable(ctx: Context): Boolean = visibility(ctx) <= floor

    fun start(svc: AccessibilityService) {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = PanicButton.sync()
        }
        val cr = svc.contentResolver
        runCatching {
            cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, o
            )
        }
        runCatching {
            cr.registerContentObserver(
                Settings.System.getUriFor("screen_brightness_float"), false, o
            )
        }
        observer = o
    }

    fun stop(svc: AccessibilityService) {
        observer?.let { runCatching { svc.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }
}
