package dev.dimmer

import android.content.Context
import android.graphics.Color

/**
 * Where the scrim colour comes from.
 *
 * SHADE_MATCH is the value solved from two native shade screenshots over
 * different backgrounds -- accurate for the wallpaper it was measured against,
 * frozen thereafter.
 *
 * SYSTEM reads the live Monet palette, so it follows the wallpaper. Android
 * exposes the whole ramp as public resources since API 31
 * (system_accent1_100, system_neutral2_200, and so on).
 */
object ScrimPalette {

    enum class Mode(val label: String) {
        SHADE_MATCH("Shade match (fixed)"),
        SYSTEM("System palette (Monet)"),
        BLACK("Black"),
        WHITE("White")
    }

    /** Solved: scrim (231,188,181) at alpha 0.98 against the native shade. */
    val MEASURED_SHADE: Int = Color.rgb(231, 188, 181)

    /**
     * Which Monet token SYSTEM mode reads. The settings screen reports the
     * closest token to MEASURED_SHADE, so this can be corrected from evidence
     * rather than guessed.
     */
    var monetToken: String = "system_accent1_100"

    fun resolve(ctx: Context, mode: Mode): Int = when (mode) {
        Mode.SHADE_MATCH -> MEASURED_SHADE
        Mode.SYSTEM -> token(ctx, monetToken) ?: MEASURED_SHADE
        Mode.BLACK -> Color.BLACK
        Mode.WHITE -> Color.WHITE
    }

    /** Resolve one system_* colour token by name. Null if it does not exist. */
    fun token(ctx: Context, name: String): Int? {
        val id = ctx.resources.getIdentifier(name, "color", "android")
        if (id == 0) return null
        return runCatching { ctx.getColor(id) }.getOrNull()
    }

    /**
     * Every system_* colour token this build exposes. Reflection rather than
     * a hardcoded list, because Android 14 added semantic tokens on top of the
     * older numbered ramp and the set keeps growing.
     */
    fun dump(ctx: Context): List<Pair<String, Int>> =
        android.R.color::class.java.fields
            .filter { it.name.startsWith("system_") }
            .mapNotNull { f ->
                runCatching { f.name to ctx.getColor(f.getInt(null)) }.getOrNull()
            }

    /** Nearest token to a target colour, by squared RGB distance. */
    fun closestTo(ctx: Context, target: Int): Triple<String, Int, Int>? =
        dump(ctx)
            .map { (n, c) -> Triple(n, c, distance(c, target)) }
            .minByOrNull { it.third }

    fun distance(a: Int, b: Int): Int {
        val dr = Color.red(a) - Color.red(b)
        val dg = Color.green(a) - Color.green(b)
        val db = Color.blue(a) - Color.blue(b)
        return dr * dr + dg * dg + db * db
    }

    fun hex(c: Int): String =
        "#%02X%02X%02X".format(Color.red(c), Color.green(c), Color.blue(c))
}
