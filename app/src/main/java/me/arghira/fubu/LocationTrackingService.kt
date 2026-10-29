package me.arghira.fubu

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class LocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        scope.launch {
            while (isActive) {
                try {
                    val loc = currentLocation()
                    if (loc == null) {
                        Log.w("FubuLocation", "No fix available this tick")
                    } else {
                        Log.d(
                            "FubuLocation",
                            "Fix ${loc.latitude} ${loc.longitude} acc=${loc.accuracy}m"
                        )
//                        LocationUploader.upload(loc)
                        AppDatabase.get(this@LocationTrackingService).locationDao().insert(Location(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            time = loc.time,
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("FubuLocation", "Tick failed", e)
                }
                delay(POLL_INTERVAL)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? =
        withTimeoutOrNull(FIX_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                LocationServices.getFusedLocationProviderClient(applicationContext)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FubuLocation", "getCurrentLocation failed", e)
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }

    private fun startInForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Location sharing",
                NotificationManager.IMPORTANCE_NONE
            )
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, LocationTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Location sharing active")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "me.arghira.fubu.STOP_TRACKING"
        private const val CHANNEL_ID = "fubu_location_quiet"
        private const val NOTIFICATION_ID = 1
        private val POLL_INTERVAL = 60_000.milliseconds
        private val FIX_TIMEOUT = 30_000.milliseconds
    }
}
