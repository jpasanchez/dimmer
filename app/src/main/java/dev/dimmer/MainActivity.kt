package dev.dimmer

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
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

/**
 * Setup and tuning surface. Built in code with framework widgets on purpose --
 * no AppCompat, no Material, no Compose, no dependencies at all.
 */
class MainActivity : Activity() {

    private lateinit var toggleBtn: Button
    private lateinit var permBtn: Button
    private lateinit var alphaLabel: TextView
    private lateinit var blurLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        permBtn = Button(this).apply {
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        toggleBtn = Button(this).apply {
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    DimmerService.toggle(this@MainActivity)
                    postDelayed({ refresh() }, 350)
                }
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
            setOnSeekBarChangeListener(simpleListener { p ->
                DimmerOverlay.setAlpha(p / 100f)
                alphaLabel.text = "Scrim alpha  ${"%.2f".format(p / 100f)}"
            })
        }

        blurLabel = TextView(this)
        val blurBar = SeekBar(this).apply {
            max = 150
            progress = DimmerOverlay.blurRadiusPx
            setOnSeekBarChangeListener(simpleListener { p ->
                DimmerOverlay.blurRadiusPx = p
                blurLabel.text = "Blur radius  ${p}px  (re-toggle to apply)"
            })
        }

        listOf(permBtn, toggleBtn, addTile, alphaLabel, alphaBar, blurLabel, blurBar)
            .forEach { root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val granted = Settings.canDrawOverlays(this)
        permBtn.text =
            if (granted) "Overlay permission granted" else "Grant \"Display over other apps\""
        permBtn.isEnabled = !granted
        toggleBtn.text = if (DimmerService.isRunning) "Turn dimmer OFF" else "Turn dimmer ON"
        toggleBtn.isEnabled = granted
        alphaLabel.text = "Scrim alpha  ${"%.2f".format(DimmerOverlay.scrimAlpha)}"
        blurLabel.text = "Blur radius  ${DimmerOverlay.blurRadiusPx}px  (re-toggle to apply)"
    }

    private fun simpleListener(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }
}
