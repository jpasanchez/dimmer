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
            setOnClickListener {
                DimmerAccessibilityService.toggle()
                postDelayed({ refresh() }, 350)
            }
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
        val alphaBar = SeekBar(this).apply {
            max = 100
            progress = (DimmerOverlay.scrimAlpha * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    DimmerOverlay.setAlpha(p / 100f)
                    alphaLabel.text = "Scrim alpha  ${"%.2f".format(p / 100f)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        val hint = TextView(this).apply {
            text = "If the screen gets stuck dark, run this from your PC:\n" +
                "adb shell settings put secure enabled_accessibility_services \"\""
            setPadding(0, pad, 0, 0)
        }

        listOf(setupBtn, toggleBtn, addTile, alphaLabel, alphaBar, hint)
            .forEach { root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val on = DimmerAccessibilityService.isConnected
        setupBtn.text = if (on) "Accessibility service running" else "Enable accessibility service"
        setupBtn.isEnabled = !on
        toggleBtn.text = if (DimmerOverlay.isShowing) "Turn dimmer OFF" else "Turn dimmer ON"
        toggleBtn.isEnabled = on
        alphaLabel.text = "Scrim alpha  ${"%.2f".format(DimmerOverlay.scrimAlpha)}"
    }
}
