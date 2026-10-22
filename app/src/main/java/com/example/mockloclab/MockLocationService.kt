package com.example.mockloclab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

class MockLocationService : Service() {
    private val channelId = "mockloc_running"
    @Volatile private var running = false
    @Volatile private var mockLat = 31.2304
    @Volatile private var mockLon = 121.4737
    @Volatile private var placeName = "测试点"
    private var worker: Thread? = null
    private lateinit var lm: LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mockLat = intent?.getDoubleExtra("lat", mockLat) ?: mockLat
        mockLon = intent?.getDoubleExtra("lon", mockLon) ?: mockLon
        placeName = intent?.getStringExtra("label") ?: placeName
        startForeground(7, notification("模拟位置：$placeName"))
        if (running) {
            // Existing worker reads volatile coordinates and adopts the latest values.
            return START_NOT_STICKY
        }
        running = true
        worker = Thread {
            try {
                installProviders()
                while (running) {
                    providers.forEach { provider ->
                        try {
                            val loc = Location(provider).apply {
                                latitude = this@MockLocationService.mockLat
                                longitude = this@MockLocationService.mockLon
                                accuracy = 3f
                                altitude = 0.0
                                bearing = 0f
                                speed = 0f
                                time = System.currentTimeMillis()
                                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                            }
                            lm.setTestProviderLocation(provider, loc)
                        } catch (e: Exception) {
                            Log.w("MockLocLab", "provider $provider failed: ${e.message}")
                        }
                    }
                    Thread.sleep(2000)
                }
            } catch (e: Exception) {
                Log.e("MockLocLab", "Mock provider error. Check Developer Options mock-location app.", e)
            } finally {
                running = false
                removeProviders()
                stopSelf()
            }
        }.also { it.start() }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun installProviders() {
        providers.forEach { provider ->
            lm.addTestProvider(
                provider, false, false, false, false,
                true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            lm.setTestProviderEnabled(provider, true)
        }
    }

    @Suppress("DEPRECATION")
    private fun removeProviders() {
        providers.forEach { provider ->
            try { lm.setTestProviderEnabled(provider, false) } catch (_: Exception) {}
            try { lm.removeTestProvider(provider) } catch (_: Exception) {}
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channelId, "模拟定位状态", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("MockLoc Lab")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        removeProviders()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
