package dev.dimmer

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.function.Consumer

class MainActivity : Activity() {

    private lateinit var setupBtn: Button
    private lateinit var toggleBtn: Button
    private lateinit var alphaLabel: TextView
    private lateinit var alphaBar: SeekBar
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

        alphaLabel = TextView(this)
        alphaBar = SeekBar(this).apply {
            max = 100
            progress = (DimmerOverlay.scrimAlpha * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) DimmerOverlay.setAlpha(p / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        readout = TextView(this).apply { setPadding(0, pad, 0, 0) }

        listOf(setupBtn, toggleBtn, addTile, alphaLabel, alphaBar, readout)
            .forEach { root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // The panic button and the QS tile both change state without going
        // through this Activity, so listen rather than poll.
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

        // Panic button resets alpha, so the slider can drift out of sync too.
        val want = (DimmerOverlay.scrimAlpha * 100).toInt()
        if (alphaBar.progress != want) alphaBar.progress = want

        alphaLabel.text = "Scrim alpha  ${"%.2f".format(DimmerOverlay.scrimAlpha)}"

        val b = VisibilityMonitor.brightness(this)
        val v = VisibilityMonitor.visibility(this)
        readout.text = buildString {
            append("Panel brightness   ${"%.0f".format(b * 100)}%\n")
            append("Est. visibility    ${"%.1f".format(v * 100)}%\n")
            append("Panic floor        ${"%.1f".format(VisibilityMonitor.floor * 100)}%")
            if (v <= VisibilityMonitor.floor) append("   <- button shows")
            append("\n\nBrightness is weighted at ")
            append("${VisibilityMonitor.brightnessWeight}")
            append(" because dimming the panel keeps contrast intact, ")
            append("while scrim alpha destroys it.")
        }
    }
}
