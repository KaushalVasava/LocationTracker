package com.lahsuak.apps.locationtracker.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lahsuak.apps.locationtracker.R
import com.lahsuak.apps.locationtracker.ui.fragments.TrackingFragment.Companion.userID
import com.lahsuak.apps.locationtracker.util.Constants
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_PAUSE_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_START_OR_RESUME_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_STOP_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.FASTEST_LOCATION_INTERVAL
import com.lahsuak.apps.locationtracker.util.Constants.LOCATION_UPDATE_INTERVAL
import com.lahsuak.apps.locationtracker.util.Constants.NOTIFICATION_CHANNEL_ID
import com.lahsuak.apps.locationtracker.util.Constants.NOTIFICATION_CHANNEL_NAME
import com.lahsuak.apps.locationtracker.util.Constants.NOTIFICATION_ID
import com.lahsuak.apps.locationtracker.util.Constants.TIMER_UPDATE_INTERVAL
import com.lahsuak.apps.locationtracker.util.TrackingUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


typealias Polyline = MutableList<LatLng>
typealias Polylines = MutableList<Polyline>

private const val TAG = "TrackingService"

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    var isFirstRun = true
    var serviceKilled = false
    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L


//    @Inject
//    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val timeRunInSeconds = MutableLiveData<Long>()

    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder

    lateinit var curNotificationBuilder: NotificationCompat.Builder

    companion object {
        val timeRunInMillis = MutableLiveData<Long>()
        val isTracking = MutableLiveData<Boolean>()
        val pathPoints = MutableLiveData<Polylines>()
    }

    private fun postInitialValues() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeRunInSeconds.postValue(0L)
        timeRunInMillis.postValue(0L)
    }

    override fun onCreate() {
        super.onCreate()
        curNotificationBuilder = baseNotificationBuilder
        postInitialValues()
        //fusedLocationProviderClient = FusedLocationProviderClient(this)

        isTracking.observe(this, Observer {
            fetch(it)
           // updateLocationTracking(it)
            updateNotificationTrackingState(it)
        })
    }

    private fun killService() {
        serviceKilled = true
        isFirstRun = true
        pauseService()
        postInitialValues()
        stopForeground(true)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                    } else {
                        Log.d(TAG, "Resuming service...")
                        startTimer()
                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    Log.d(TAG, "Paused service")
                    pauseService()
                }
                ACTION_STOP_SERVICE -> {
                    Log.d(TAG, "Stopped service")
                    killService()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                // time difference between now and timeStarted
                lapTime = System.currentTimeMillis() - timeStarted
                // post the new lapTime
                timeRunInMillis.postValue(timeRun + lapTime)
                if (timeRunInMillis.value!! >= lastSecondTimestamp + 1000L) {
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 1)
                    lastSecondTimestamp += 1000L
                }
                delay(TIMER_UPDATE_INTERVAL)
            }
            timeRun += lapTime
        }
    }

    private fun pauseService() {
        isTracking.postValue(false)
        isTimerEnabled = false
    }

    private fun updateNotificationTrackingState(isTracking: Boolean) {
        val notificationActionText = if (isTracking) "Pause" else "Resume"
        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        curNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(curNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }
        if (!serviceKilled) {
            curNotificationBuilder = baseNotificationBuilder
                .addAction(R.drawable.ic_pause, notificationActionText, pendingIntent)
            notificationManager.notify(NOTIFICATION_ID, curNotificationBuilder.build())
        }
    }

    private fun fetch(isTracking: Boolean) {
        var count = 0
        Log.d(TAG, "fetch: $isTracking")
        val fireRef = FirebaseDatabase.getInstance().reference.child("LTUsers").child(userID)
        fireRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isTracking) {
                    val latitude = snapshot.child("lat").getValue(Double::class.java)
                    val longitude = snapshot.child("lng").getValue(Double::class.java)
                    val latLng = LatLng(latitude!!, longitude!!)
                    addPathPoint(latLng)
                   // if (count == 0) {
                        //deviceCurrentLocation = latLng

//                        map?.addMarker(
//                            MarkerOptions().position(LatLng(latitude, longitude))
//                                .title("Marker in ${LatLng(latitude, longitude)}")
//                        )
//                        map?.animateCamera(
//                            CameraUpdateFactory.newLatLngZoom(
//                                latLng, Constants.MAP_ZOOM
//                            )
//                        )
                    //}
//                    map?.animateCamera(
//                        CameraUpdateFactory.newLatLngZoom(
//                            latLng, Constants.MAP_ZOOM
//                        )
//                    )
                   // count++
                 }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {
//            if (TrackingUtility.hasLocationPermissions(this)) {
//                val request = LocationRequest().apply {
//                    interval = LOCATION_UPDATE_INTERVAL
//                    fastestInterval = FASTEST_LOCATION_INTERVAL
//                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//                }
//                fusedLocationProviderClient.requestLocationUpdates(
//                    request,
//                    locationCallback,
//                    Looper.getMainLooper()
//                )
//            }
//        } else {
//            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
//        }
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking.value!!) {
                result.locations.let { locations ->
                    for (location in locations) {
                       // addPathPoint(location)
                        Log.d(TAG, "NEW LOCATION: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    private fun addPathPoint(latLng: LatLng) {
       // location?.let {
            val pos = latLng//LatLng(latitude, location.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        //}
    }

    private fun addEmptyPolyline() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

    private fun startForegroundService() {
        startTimer()
        isTracking.postValue(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

        timeRunInSeconds.observe(this, Observer {
            if (!serviceKilled) {
                val notification = curNotificationBuilder
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(it * 1000L))
                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}