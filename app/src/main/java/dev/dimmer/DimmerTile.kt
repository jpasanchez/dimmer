package dev.dimmer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DimmerTile : TileService() {

    companion object {
        fun refresh(ctx: Context) {
            TileService.requestListeningState(
                ctx, ComponentName(ctx, DimmerTile::class.java)
            )
        }
    }

    override fun onStartListening() = sync()

    override fun onClick() {
        // Not enabled yet -- send them to setup instead of failing silently.
        if (!DimmerAccessibilityService.toggle()) {
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
            return
        }
        sync()
    }

    private fun sync() {
        qsTile?.apply {
            state = when {
                !DimmerAccessibilityService.isConnected -> Tile.STATE_UNAVAILABLE
                DimmerOverlay.isShowing -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            subtitle = when {
                !DimmerAccessibilityService.isConnected -> "Setup needed"
                DimmerOverlay.isShowing -> "On"
                else -> "Off"
            }
            updateTile()
        }
    }
}
