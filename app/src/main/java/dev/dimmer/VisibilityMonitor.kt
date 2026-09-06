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
 * Brightness and scrim alpha are NOT interchangeable:
 *  - Low panel brightness scales everything uniformly. Contrast survives and
 *    the eye adapts, so content stays legible -- just dim.
 *  - Scrim alpha destroys contrast by pulling every pixel toward one colour.
 *    Adaptation cannot recover what is no longer there.
 *
 * So alpha dominates and brightness is a weak modifier, applied as a root.
 */
object VisibilityMonitor {

    var floor: Float = 0.03f

    /** 0 ignores brightness; 1 makes it as important as alpha. */
    var brightnessWeight: Float = 0.25f

    // --- Diagnostics. Which brightness key a build actually maintains varies,
    // --- so expose the raw reads rather than trusting one.
    var observerFires: Int = 0; private set
    var observerRegistered: Boolean = false; private set

    private var observer: ContentObserver? = null

    /** Android 12+ keeps a float copy; the key is not a public constant. */
    fun rawFloat(ctx: Context): Float? =
        runCatching { Settings.System.getFloat(ctx.contentResolver, "screen_brightness_float") }
            .getOrNull()

    /** Legacy 0..255 value. May go stale when adaptive brightness is on. */
    fun rawInt(ctx: Context): Int? =
        runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()

    /** 1 = automatic/adaptive, 0 = manual. */
    fun autoMode(ctx: Context): Int? =
        runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrNull()

    /** Panel brightness 0..1. Falls back to 1.0 (fail safe: no false panic). */
    fun brightness(ctx: Context): Float {
        rawFloat(ctx)?.let { if (it in 0f..1f) return it }
        rawInt(ctx)?.let { return (it / 255f).coerceIn(0f, 1f) }
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
            override fun onChange(selfChange: Boolean) {
                observerFires++
                PanicButton.sync()
                // Was missing: the UI had no reason to redraw, so the
                // brightness readout went stale.
                DimmerOverlay.onStateChanged?.invoke()
            }
        }
        val cr = svc.contentResolver
        var ok = false
        runCatching {
            cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, o
            ); ok = true
        }
        runCatching {
            cr.registerContentObserver(
                Settings.System.getUriFor("screen_brightness_float"), false, o
            ); ok = true
        }
        observer = o
        observerRegistered = ok
    }

    fun stop(svc: AccessibilityService) {
        observer?.let { runCatching { svc.contentResolver.unregisterContentObserver(it) } }
        observer = null
        observerRegistered = false
    }
}
