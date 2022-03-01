package com.lahsuak.apps.locationtracker.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lahsuak.apps.locationtracker.R
import com.lahsuak.apps.locationtracker.service.Polyline
import com.lahsuak.apps.locationtracker.service.TrackingService
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_PAUSE_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_START_OR_RESUME_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_STOP_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.MAP_ZOOM
import com.lahsuak.apps.locationtracker.util.Constants.POLYLINE_COLOR
import com.lahsuak.apps.locationtracker.util.Constants.POLYLINE_WIDTH
import com.lahsuak.apps.locationtracker.util.Constants.REQUEST_CODE_LOCATION_PERMISSION
import com.lahsuak.apps.locationtracker.util.TrackingUtility
import com.lahsuak.apps.locationtracker.util.TrackingUtility.getFormattedStopWatchTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.*
import javax.inject.Inject
import kotlin.math.round

private const val TAG = "TAG"

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking), EasyPermissions.PermissionCallbacks {
    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()
    private val args: TrackingFragmentArgs by navArgs()
    private var map: GoogleMap? = null

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var mLocationPermissionsGranted = false
    private lateinit var deviceCurrentLocation: LatLng
    private var curTimeInMillis = 0L

    companion object {
        lateinit var userID: String
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView.onCreate(savedInstanceState)
        userID = args.userId
        requestPermissions()
        startBtn.setOnClickListener {
            toggleRun()
        }

        mapView.getMapAsync {
            map = it
            addAllPolylines()
            if (mLocationPermissionsGranted) {
                map?.isMyLocationEnabled = true
                getDeviceLocation()
            }
        }

        subscribeToObservers()
    }

    private fun getDeviceLocation() {
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        try {
            if (mLocationPermissionsGranted) {
                val location = fusedLocationProviderClient.lastLocation
                location.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        try {
                            val currentLocation = task.result as Location

                            Log.d(TAG, "getDeviceLocation: $currentLocation")

                            val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                            saveData(latLng)
//                          addPolyLines(latLng)

                            // deviceCurrentLocation = latLng
                            //map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15F))
                        } catch (e: Exception) {
                            Toast.makeText(
                                requireContext().applicationContext,
                                "Please turn on your GPS",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.d(TAG, "getDeviceLocation: current location is null")
                        Toast.makeText(
                            requireContext(),
                            "Not found your current location",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "getDeviceLocation: Security Exception :${e.message}")
        }
    }

    private fun saveData(latLng: LatLng) {
        val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        val userRef = FirebaseDatabase.getInstance().reference.child("LTUsers")

        val pref = requireContext().getSharedPreferences("LOGIN_DATA", MODE_PRIVATE)
        val phNumber: String = pref.getString("phoneNo", null) ?: "null"
        val name: String = pref.getString("userName", "Lenovo") ?: "null"

        //val d = "Ov4dZxbvtXgRGyvHZHnyeyczlKV1"

        val userMap = HashMap<String, Any>()
        userMap["uid"] = currentUserId
        userMap["name"] = name
        userMap["phoneNumber"] = phNumber//1111222200//phNumber
        userMap["lat"] = latLng.latitude
        userMap["lng"] = latLng.longitude

        userRef.child(currentUserId).setValue(userMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "saveData: lat ${latLng.latitude} and long: ${latLng.longitude}")
                    Toast.makeText(
                        requireContext(),
                        "Location added successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    FirebaseAuth.getInstance().signOut()
                }
            }
    }

    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(viewLifecycleOwner, {
            updateTracking(it)

        })

        TrackingService.pathPoints.observe(viewLifecycleOwner, {
            pathPoints = it
            addLatestPolyline()
            moveCameraToUser()
        })

        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, {
            curTimeInMillis = it
            val formattedTime = getFormattedStopWatchTime(curTimeInMillis, true)
            //tvTimer.text = formattedTime
        })
    }

    private fun toggleRun() {
        if (isTracking) {
            //menu?.getItem(0)?.isVisible = true
            sendCommandToService(ACTION_STOP_SERVICE)//ACTION_PAUSE_SERVICE)
            isTracking = false
            zoomToSeeWholeTrack()
            endRunAndSaveToDb()
            startBtn.setImageResource(R.drawable.ic_play)
        } else {
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
            startBtn.setImageResource(R.drawable.ic_pause)
            isTracking = true
            //fetch()
        }
    }

    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if (!isTracking) {
            startBtn.setImageResource(R.drawable.ic_play)
//            btnToggleRun.text = "Start"
//            btnFinishRun.visibility = View.VISIBLE
        } else {
            startBtn.setImageResource(R.drawable.ic_pause)
            //btnToggleRun.text = "Stop"
            // menu?.getItem(0)?.isVisible = true
            //btnFinishRun.visibility = View.GONE
        }
    }

    private fun moveCameraToUser() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    private fun zoomToSeeWholeTrack() {
        val bounds = LatLngBounds.Builder()
        for (polyline in pathPoints) {
            for (pos in polyline) {
                bounds.include(pos)
            }
        }

        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt()
            )
        )
    }

    private fun endRunAndSaveToDb() {
//        map?.snapshot { bmp ->
//            var distanceInMeters = 0
//            for(polyline in pathPoints) {
//                distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
//            }
//            val avgSpeed = round((distanceInMeters / 1000f) / (curTimeInMillis / 1000f / 60 / 60) * 10) / 10f
//            val dateTimestamp = Calendar.getInstance().timeInMillis
//            val caloriesBurned = ((distanceInMeters / 1000f) * weight).toInt()
//            val run = Run(bmp, dateTimestamp, avgSpeed, distanceInMeters, curTimeInMillis, caloriesBurned)
//            viewModel.insertRun(run)
//            Snackbar.make(
//                requireActivity().findViewById(R.id.rootView),
//                "Run saved successfully",
//                Snackbar.LENGTH_LONG
//            ).show()
//            stopRun()
//        }
    }

    private fun addAllPolylines() {
        for (polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map?.addPolyline(polylineOptions)
        }
    }

    private fun addLatestPolyline() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)
            map?.addPolyline(polylineOptions)
        }
    }

    private fun sendCommandToService(action: String) =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    private fun requestPermissions() {
        if (TrackingUtility.hasLocationPermissions(requireContext())) {
            mLocationPermissionsGranted = true
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        } else {
            requestPermissions()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        mLocationPermissionsGranted = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        @Suppress("deprecation")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

}


/*
package com.lahsuak.apps.locationtracker.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lahsuak.apps.locationtracker.R
import com.lahsuak.apps.locationtracker.databinding.FragmentTrackingBinding
import com.lahsuak.apps.locationtracker.service.Polyline
import com.lahsuak.apps.locationtracker.service.TrackingService
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_PAUSE_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_START_OR_RESUME_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.ACTION_STOP_SERVICE
import com.lahsuak.apps.locationtracker.util.Constants.FASTEST_LOCATION_INTERVAL
import com.lahsuak.apps.locationtracker.util.Constants.LOCATION_UPDATE_INTERVAL
import com.lahsuak.apps.locationtracker.util.Constants.MAP_ZOOM
import com.lahsuak.apps.locationtracker.util.Constants.POLYLINE_COLOR
import com.lahsuak.apps.locationtracker.util.Constants.POLYLINE_WIDTH
import com.lahsuak.apps.locationtracker.util.Constants.REQUEST_CODE_LOCATION_PERMISSION
import com.lahsuak.apps.locationtracker.util.TrackingUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.lang.Exception
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.round

private const val TAG = "TrackingFragment"

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking), EasyPermissions.PermissionCallbacks {

    private var mLocationPermissionsGranted = false

    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()

    private val FINE_LOCATION: String = Manifest.permission.ACCESS_FINE_LOCATION
    private val COURSE_LOCATION: String = Manifest.permission.ACCESS_COARSE_LOCATION
    private val LOCATION_PERMISSION_REQUEST_CODE = 1234

    private val args: TrackingFragmentArgs by navArgs()
    private lateinit var binding: FragmentTrackingBinding
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    private lateinit var map: GoogleMap
    private lateinit var deviceCurrentLocation: LatLng

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentTrackingBinding.bind(view)
        binding.mapView.onCreate(savedInstanceState)

        binding.mapView.getMapAsync { it1 ->
            map = it1
            getLocationPermission()

            if (mLocationPermissionsGranted) {
                getDeviceLocation()
                map.isMyLocationEnabled = true

                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@getMapAsync
                }
            }
        }
        startBtn.setOnClickListener {
            toggleRun()
        }
        //fetch()
//        sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
//        subscribeToObservers()
    }

    private fun fetch() {
        var count = 0
        val fireRef = FirebaseDatabase.getInstance().reference.child("LTUsers").child(args.userId)
        fireRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isTracking) {
                    val latitude = snapshot.child("lat").getValue(Double::class.java)
                    val longitude = snapshot.child("lng").getValue(Double::class.java)
                    var latLng = LatLng(latitude!!,longitude!!)
                    if (count == 0) {
                        deviceCurrentLocation = latLng

                        map.addMarker(
                            MarkerOptions().position(LatLng(latitude, longitude))
                                .title("Marker in ${LatLng(latitude, longitude)}")
                        )
                        map.animateCamera (
                            CameraUpdateFactory.newLatLngZoom(
                                latLng, MAP_ZOOM)
                        )
                    }

                    count++
                    pathPoints.add(latLng)
                    val bounds = LatLngBounds.Builder()
                    for(polyline in pathPoints) {
                       // for(pos in polyline) {
                            bounds.include(polyline)
                        //}
                    }

                    map.moveCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bounds.build(),
                            mapView.width,
                            mapView.height,
                            (mapView.height * 0.05f).toInt()
                        )
                    )

                    updateLocationTracking(isTracking)
//                    map.moveCamera(
//                        CameraUpdateFactory.newLatLngBounds(
//                            bounds.build(),
//                            mapView.width,
//                            mapView.height,
//                            (mapView.height * 0.05f).toInt()
//                        )
//                    )
                    if (count > 0)
                        addPolyLines(LatLng(latitude, longitude))
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    private fun addPolyLines(latLng: LatLng) {
        val pOptions = PolylineOptions()
            .add(latLng)
            .add(deviceCurrentLocation)
            .color(POLYLINE_COLOR)
            .width(POLYLINE_WIDTH)

        map.addPolyline(pOptions)

    }

    private fun saveData(latLng: LatLng) {
        val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        val userRef = FirebaseDatabase.getInstance().reference.child("LTUsers").child(currentUserId)

        val pref = requireContext().getSharedPreferences("LOGIN_DATA", MODE_PRIVATE)
        val phNumber: String = pref.getString("phoneNo", null) ?: "null"

        //val d = "Ov4dZxbvtXgRGyvHZHnyeyczlKV1"

        val userMap = HashMap<String, Any>()
        userMap["uid"] = args.userId
        userMap["phoneNumber"] = phNumber//1111222200//phNumber
        userMap["lat"] = latLng.latitude
        userMap["lng"] = latLng.longitude

        userRef.child(currentUserId).setValue(userMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Location added successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    FirebaseAuth.getInstance().signOut()
                }
            }
    }

    private fun getDeviceLocation() {
        mFusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        try {
            if (mLocationPermissionsGranted) {
                val location = mFusedLocationProviderClient.lastLocation
                location.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        try {
                            val currentLocation = task.result as Location

                            Log.d(TAG, "getDeviceLocation: $currentLocation")

                            val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                            saveData(latLng)
                            addPolyLines(latLng)

                            // deviceCurrentLocation = latLng
                            //map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15F))
                        } catch (e: Exception) {
                            Toast.makeText(
                                requireContext().applicationContext,
                                "Please turn on your GPS",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.d(TAG, "getDeviceLocation: current location is null")
                        Toast.makeText(
                            requireContext(),
                            "Not found your current location",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "getDeviceLocation: Security Exception :${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    private fun getLocationPermission() {
        Log.d(TAG, "getLocationPermission: getting location permissions")
        val permissions = arrayOf<String>(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (ContextCompat.checkSelfPermission(
                requireContext().applicationContext,
                FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (ContextCompat.checkSelfPermission(
                    requireContext().applicationContext,
                    COURSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mLocationPermissionsGranted = true
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    permissions,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissions,
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String?>,
//        grantResults: IntArray
//    ) {
//        @Suppress("deprecation")
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        mLocationPermissionsGranted = false
//        when (requestCode) {
//            LOCATION_PERMISSION_REQUEST_CODE -> {
//                if (grantResults.size > 0) {
//                    var i = 0
//                    while (i < grantResults.size) {
//                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
//                            mLocationPermissionsGranted = false
//                            return
//                        }
//                        i++
//                    }
//                    mLocationPermissionsGranted = true
//                }
//            }
//        }
//    }


    private fun toggleRun() {
        if (isTracking) {
            isTracking = false
            startBtn.setImageResource(R.drawable.ic_play)
            //sendCommandToService(ACTION_PAUSE_SERVICE)
        } else {
            startBtn.setImageResource(R.drawable.ic_pause)
            fetch()
            isTracking = true
            //sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    private fun requestPermissions() {
        if (TrackingUtility.hasLocationPermissions(requireContext())) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        } else {
            requestPermissions()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {}

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        @Suppress("deprecation")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    /** EXTRA METHODS FOR FUTURE*/

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {
        if (isTracking) {
            if (TrackingUtility.hasLocationPermissions(requireContext())) {
                val request = LocationRequest().apply {
                    interval = LOCATION_UPDATE_INTERVAL
                    fastestInterval = FASTEST_LOCATION_INTERVAL
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                }
                mFusedLocationProviderClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } else {
            mFusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking) {
                result.locations.let { locations ->
                    for (location in locations) {
                        pathPoints.add(LatLng(location.latitude,location.longitude))
                        // addPathPoint(location)
                        Log.d(TAG,"NEW LOCATION: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }
/*

    private fun showCancelTrackingDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel the Run?")
            .setMessage("Are you sure to cancel the current run and delete all its data?")
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Yes") { _, _ ->
                stopRun()
            }
            .setNegativeButton("No") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
        dialog.show()
    }

    private fun stopRun() {
        sendCommandToService(ACTION_STOP_SERVICE)
        // findNavController().navigate(R.id.action_trackingFragment_to_runFragment)
    }

    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if (!isTracking) {
//            btnToggleRun.text = "Start"
//            btnFinishRun.visibility = View.VISIBLE
        } else {
//            btnToggleRun.text = "Stop"
//            menu?.getItem(0)?.isVisible = true
//            btnFinishRun.visibility = View.GONE
        }
    }

    private fun moveCameraToUser() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    private fun zoomToSeeWholeTrack() {
        val bounds = LatLngBounds.Builder()
        for (polyline in pathPoints) {
            for (pos in polyline) {
                bounds.include(pos)
            }
        }

        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt()
            )
        )
    }

    private fun endRunAndSaveToDb() {
        map?.snapshot { bmp ->
            //viewModel.insertRun(run)
            Snackbar.make(
                binding.root,
                "Run saved successfully",
                Snackbar.LENGTH_LONG
            ).show()
            stopRun()
        }
    }

    private fun addAllPolylines() {
        for (polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map.addPolyline(polylineOptions)
        }
    }

    private fun addLatestPolyline() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)
            map.addPolyline(polylineOptions)
        }
    }

    private fun sendCommandToService(action: String) =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }
*/

//    private fun subscribeToObservers() {
//        TrackingService.isTracking.observe(viewLifecycleOwner, {
//            updateTracking(it)
//        })
//
//        TrackingService.pathPoints.observe(viewLifecycleOwner, {
//            pathPoints = it
//            addLatestPolyline()
//            moveCameraToUser()
//        })
//    }
}*/