package dev.dimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder

/**
 * Holds the overlay alive. A foreground service is mandatory: WindowManager
 * keeps the view only as long as the process lives, and a process with no
 * running component is fair game for the OOM killer.
 */
class DimmerService : Service() {

    companion object {
        const val ACTION_ON = "dev.dimmer.ON"
        const val ACTION_OFF = "dev.dimmer.OFF"
        private const val CHANNEL = "dimmer"
        private const val NOTIF_ID = 1

        @Volatile
        var isRunning = false
            private set

        fun on(ctx: Context) {
            ctx.startForegroundService(
                Intent(ctx, DimmerService::class.java).setAction(ACTION_ON)
            )
        }

        fun off(ctx: Context) {
            ctx.startService(
                Intent(ctx, DimmerService::class.java).setAction(ACTION_OFF)
            )
        }

        fun toggle(ctx: Context) = if (isRunning) off(ctx) else on(ctx)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        DimmerOverlay.show(this)
        isRunning = true
        DimmerTile.refresh(this)
        return START_STICKY
    }

    override fun onDestroy() {
        DimmerOverlay.hide(this)
        isRunning = false
        DimmerTile.refresh(this)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Dimmer", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
        }
        val offIntent = PendingIntent.getService(
            this, 0,
            Intent(this, DimmerService::class.java).setAction(ACTION_OFF),
            PendingIntent.FLAG_IMMUTABLE
        )
        // The null needs an explicit type or Kotlin can't pick between the
        // Icon and the deprecated int overload.
        val offAction = Notification.Action.Builder(
            null as Icon?, "Turn off", offIntent
        ).build()

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_dimmer)
            .setContentTitle("Dimmer on")
            .setOngoing(true)
            .addAction(offAction)
            .build()
    }
}
