package dev.dimmer

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import java.util.function.Consumer
import kotlin.math.roundToInt

class MainActivity : Activity() {

    // Coarse sets a base; fine sweeps ALL the headroom left between that base
    // and 1.0. At base 0.98 the fine slider covers visible 2.00% -> 0.00%,
    // which is the whole remaining ghost.
    //
    // Both positions are stored rather than derived from alpha. That is what
    // stops the fine slider from dragging the coarse one to the top: alpha is
    // the output of the pair, never the source of truth for either.
    private var baseProgress = 95        // hundredths of alpha
    private var fineProgress = 0         // percent of the remaining headroom

    private lateinit var setupBtn: Button
    private lateinit var toggleBtn: Button
    private lateinit var modeSpinner: Spinner
    private lateinit var alphaLabel: TextView
    private lateinit var coarseBar: SeekBar
    private lateinit var fineLabel: TextView
    private lateinit var fineBar: SeekBar
    private lateinit var readout: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        setupBtn = Button(this).apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        toggleBtn = Button(this).apply {
            setOnClickListener { DimmerAccessibilityService.toggle() }
        }

        val addTile = Button(this).apply {
            text = "Add Quick Settings tile"
            setOnClickListener {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this@MainActivity, DimmerTile::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this@MainActivity, R.drawable.ic_dimmer),
                    mainExecutor,
                    Consumer<Int> { }
                )
            }
        }

        val modes = ScrimPalette.Mode.entries
        modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                modes.map { it.label }
            )
            setSelection(modes.indexOf(DimmerOverlay.mode))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (modes[pos] != DimmerOverlay.mode) {
                        DimmerOverlay.setMode(this@MainActivity, modes[pos])
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        splitFromAlpha(DimmerOverlay.scrimAlpha)

        alphaLabel = TextView(this)
        coarseBar = SeekBar(this).apply {
            max = 100
            progress = baseProgress
            setOnSeekBarChangeListener(listener { p -> baseProgress = p; applySliders() })
        }
        fineLabel = TextView(this).apply { setPadding(0, pad / 2, 0, 0) }
        fineBar = SeekBar(this).apply {
            max = 100
            progress = fineProgress
            setOnSeekBarChangeListener(listener { p -> fineProgress = p; applySliders() })
        }

        readout = TextView(this).apply { setPadding(0, pad, 0, 0) }

        listOf(
            setupBtn, toggleBtn, addTile, modeSpinner,
            alphaLabel, coarseBar, fineLabel, fineBar, readout
        ).forEach { root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        setContentView(root)
    }

    private fun baseAlpha() = baseProgress / 100f

    /** alpha = base + fine% of whatever is left between base and 1.0 */
    private fun combined(): Float {
        val b = baseAlpha()
        return (b + (fineProgress / 100f) * (1f - b)).coerceIn(0f, 1f)
    }

    private fun applySliders() = DimmerOverlay.setAlpha(combined())

    /** Recover both positions from an alpha set elsewhere (first launch). */
    private fun splitFromAlpha(a: Float) {
        baseProgress = (a * 100).toInt().coerceIn(0, 100)
        val b = baseAlpha()
        val headroom = 1f - b
        fineProgress =
            if (headroom <= 0f) 0
            else (((a - b) / headroom) * 100).roundToInt().coerceIn(0, 100)
    }

    /** Programmatic progress changes must not feed back as user input. */
    private fun listener(onUser: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onUser(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }

    override fun onResume() {
        super.onResume()
        DimmerOverlay.onStateChanged = { runOnUiThread { refresh() } }
        refresh()
    }

    override fun onPause() {
        DimmerOverlay.onStateChanged = null
        super.onPause()
    }

    private fun refresh() {
        val on = DimmerAccessibilityService.isConnected
        setupBtn.text = if (on) "Accessibility service running" else "Enable accessibility service"
        setupBtn.isEnabled = !on
        toggleBtn.text = if (DimmerOverlay.isShowing) "Turn dimmer OFF" else "Turn dimmer ON"
        toggleBtn.isEnabled = on

        val a = DimmerOverlay.scrimAlpha
        val visible = (1f - a) * 100f

        // Sliders are the source of truth, so only push if something outside
        // this screen moved alpha. The guard also stops a programmatic set
        // from cancelling the drag that caused it.
        if (coarseBar.progress != baseProgress) coarseBar.progress = baseProgress
        if (fineBar.progress != fineProgress) fineBar.progress = fineProgress

        alphaLabel.text =
            "Scrim alpha  ${"%.4f".format(a)}     Visible  ${"%.2f".format(visible)}%"

        val b0 = baseAlpha()
        fineLabel.text =
            if (baseProgress >= 100) "Fine  (base at 1.0, no headroom left)"
            else "Fine  ${fineProgress}%  of the headroom above ${"%.2f".format(b0)}" +
                "     sweeps visible ${"%.2f".format((1f - b0) * 100)}% to 0.00%"

        val b = VisibilityMonitor.brightness(this)
        val v = VisibilityMonitor.visibility(this)
        val monet = ScrimPalette.token(this, ScrimPalette.monetToken)
        val near = ScrimPalette.closestTo(this, ScrimPalette.MEASURED_SHADE)

        readout.text = buildString {
            append("Scrim colour    ${ScrimPalette.hex(DimmerOverlay.scrimColor)}\n")
            append("Measured shade  ${ScrimPalette.hex(ScrimPalette.MEASURED_SHADE)}\n")
            append("${ScrimPalette.monetToken}  ")
            append(if (monet != null) ScrimPalette.hex(monet) else "not available")
            append("\n\nClosest token to measured shade:\n    ")
            if (near != null) {
                append("${near.first}  ${ScrimPalette.hex(near.second)}  (dist ${near.third})")
            } else append("none found")

            append("\n\n--- brightness diagnostics ---\n")
            append("screen_brightness_float  ")
            append(VisibilityMonitor.rawFloat(this@MainActivity)?.let { "%.3f".format(it) } ?: "absent")
            append("\nscreen_brightness (int)  ")
            append(VisibilityMonitor.rawInt(this@MainActivity)?.toString() ?: "absent")
            append("\nadaptive mode            ")
            append(when (VisibilityMonitor.autoMode(this@MainActivity)) {
                1 -> "automatic"; 0 -> "manual"; else -> "unknown"
            })
            append("\nobserver                 ")
            append(if (VisibilityMonitor.observerRegistered) "registered" else "NOT registered")
            append(", ${VisibilityMonitor.observerFires} fires")

            append("\n\nUsing ${"%.0f".format(b * 100)}%")
            append("   Est. visibility ${"%.1f".format(v * 100)}%")
            append("\n\nNative shade measures ~2.0% visible.")
            append("\nStuck? Double volume press disables the service.")
        }
    }
}
