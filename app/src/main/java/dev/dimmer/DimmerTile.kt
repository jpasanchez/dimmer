package dev.dimmer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The trigger. You pull the shade with the real system gesture and tap this.
 *
 * You cannot intercept the top-edge swipe yourself -- SystemUI owns it, and
 * any overlay that tried to catch it would have to be touchable, which means
 * swallowing taps from every app underneath.
 */
class DimmerTile : TileService() {

    companion object {
        /** Nudge the tile to re-read state even while the shade is closed. */
        fun refresh(ctx: Context) {
            TileService.requestListeningState(
                ctx, ComponentName(ctx, DimmerTile::class.java)
            )
        }
    }

    override fun onStartListening() {
        sync(DimmerService.isRunning)
    }

    override fun onClick() {
        if (!Settings.canDrawOverlays(this)) {
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
            // PendingIntent overload is API 34+. The Intent overload throws there.
            startActivityAndCollapse(pi)
            return
        }
        val willBeOn = !DimmerService.isRunning
        DimmerService.toggle(this)
        sync(willBeOn)   // optimistic; the service refreshes us once it settles
    }

    private fun sync(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (on) "On" else "Off"
            updateTile()
        }
    }
}
