package com.example.positiontracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.room.Room
import java.io.IOException
import java.time.LocalDateTime
import kotlin.math.roundToLong

class posTrackService : LifecycleService() {
    val ACTION_START_OR_RESUME_SERVICE = "ACTION_START_OR_RESUME_SERVICE"
    val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

    val NOTIFICATION_CHANNEL_ID = "tracking_channel"
    val NOTIFICATION_CHANNEL_NAME = "Tracking"
    val NOTIFICATION_ID = 2
    private var locationManager: LocationManager? = null
    val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

    private var context: Context = this
    var time: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?

        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    println("Started or resumed")
                    startForegroundService()
                    time = System.currentTimeMillis()

                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                    }
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0f,
                        locationListener
                    )
                }
                ACTION_STOP_SERVICE -> {
                    locationManager?.removeUpdates(locationListener)
                    println("Stoped")
                    stopForeground(true)
                }

                else -> {
                    println("Else")
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }


        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle("GPS Tracker")
            .setContentText("Loading")
            .build()
        startForeground(101, notification)

    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private val locationListener: LocationListener = object : LocationListener {
        var oldLocation: Location? = null
        var distanceTraveled: Double = 0.0
        override fun onLocationChanged(location: Location) {
            //pushToChat(location.longitude.toString())

            val db = Room.databaseBuilder(
                applicationContext,
                MainActivity.AppDatabase::class.java, "positionsList"
            ).build()

            try {
                Thread(Runnable {

                    val posDao = db.positionDao()

                    val pos = MainActivity.Position(
                        LocalDateTime.now().toString(),
                        location.latitude,
                        location.longitude
                    )


                    println(location.accuracy)

                    var title = "GPS Tracker"

                    if (location.accuracy < 5) {
                        if (oldLocation != null) {
                            var distance = location.distanceTo(oldLocation)
                            distanceTraveled += distance
                            println(distanceTraveled)
                        }
                        oldLocation = location
                        posDao.insertAll(pos)

                    } else {
                        title = "GPS Tracker, WARING LOW ACCURACY (" + location.accuracy.toInt().toString() + " meters)"
                    }

                    val count = posDao.getCount()

                    println(count)

                    val notification = notificationBuilder.setOngoing(true)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .setContentTitle(title)
                        .setContentText(
                            count.toString() + " data points, " + (((System.currentTimeMillis()
                                .toDouble() - time.toDouble()) / 600).toInt()
                                .toDouble() / 100).toString() + " minutes, " + distanceTraveled.toInt()
                                .toString() + " meters"
                        )

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                            as NotificationManager
                    notificationManager.notify(101, notification.build())

                }).start()

            } catch (e: IOException) {
                println("Error puck")
                e.printStackTrace()
            }

        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}