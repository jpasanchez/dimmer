package dev.dimmer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Reads panel brightness and estimates how much light reaches the eye.
 *
 * No longer gates anything -- it drove the old on-screen panic button, which
 * the system accessibility shortcut replaced. Kept because the readout is
 * useful while tuning alpha and colour, and because the ContentObserver is
 * what keeps that readout live: pulling the shade to reach the brightness
 * slider does not pause the Activity, so onResume never fires.
 *
 * Brightness is weighted low on purpose. Dimming the panel scales everything
 * uniformly -- contrast survives and the eye adapts. Scrim alpha destroys
 * contrast outright, and adaptation cannot recover what is gone.
 */
object VisibilityMonitor {

    /** 0 ignores brightness; 1 makes it as important as alpha. */
    var brightnessWeight: Float = 0.25f

    var observerFires: Int = 0; private set
    var observerRegistered: Boolean = false; private set

    private var observer: ContentObserver? = null

    /** Android 12+ keeps a float copy; the key is not a public constant. */
    fun rawFloat(ctx: Context): Float? =
        runCatching { Settings.System.getFloat(ctx.contentResolver, "screen_brightness_float") }
            .getOrNull()

    /** Legacy 0..255 value. */
    fun rawInt(ctx: Context): Int? =
        runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()

    /** 1 = automatic/adaptive, 0 = manual. */
    fun autoMode(ctx: Context): Int? =
        runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrNull()

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

    fun start(svc: AccessibilityService) {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                observerFires++
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
