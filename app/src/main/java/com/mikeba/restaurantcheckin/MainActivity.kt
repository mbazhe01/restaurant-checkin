package com.mikeba.restaurantcheckin

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places

import com.mikeba.restaurantcheckin.BuildConfig
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RC_LOC"
        private const val REQ_LOCATION = 100
    }

    private lateinit var statusText: TextView
    private lateinit var checkInButton: Button

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    // 👇 PUT IT HERE (class-level variable)
    private lateinit var placesClient: PlacesClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }


        placesClient = Places.createClient(this)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkInButton = findViewById(R.id.btnCheckIn)
        statusText = findViewById(R.id.tvStatus)

        statusText.text = "Status: idle"

        checkInButton.setOnClickListener {
            Log.d(TAG, "Check In clicked")

            if (!hasLocationPermission()) {
                Log.d(TAG, "No permission -> requesting")
                statusText.text = "Status: requesting location permission..."
                requestLocationPermission()
                return@setOnClickListener
            }

            statusText.text = "Status: getting location..."
            getOneTimeLocation(
                onSuccess = { lat, lng, source ->
                    Log.d(TAG, "SUCCESS lat=$lat lng=$lng source=$source")
                    statusText.text = "Latitude: %.6f\nLongitude: %.6f\n(%s)".format(lat, lng, source)
                },
                onError = { err ->
                    Log.e(TAG, "ERROR $err")
                    statusText.text = "Status: $err"
                }
            )

            fetchRestaurantName { name ->
                statusText.text = statusText.text.toString() + "\n\nRestaurant: $name"
            }


        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQ_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_LOCATION) {
            val granted = hasLocationPermission()
            Log.d(TAG, "onRequestPermissionsResult -> granted=$granted")
            statusText.text = if (granted) {
                "Status: permission granted. Tap Check In."
            } else {
                "Status: permission denied."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getOneTimeLocation(
        onSuccess: (lat: Double, lng: Double, source: String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "getOneTimeLocation() start")

        // 1) Try current location (can be null on emulator)
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                Log.d(TAG, "getCurrentLocation success: $loc")
                if (loc != null) {
                    onSuccess(loc.latitude, loc.longitude, "current")
                    return@addOnSuccessListener
                }

                // 2) Fallback to last known
                fused.lastLocation
                    .addOnSuccessListener { last ->
                        Log.d(TAG, "lastLocation success: $last")
                        if (last != null) {
                            onSuccess(last.latitude, last.longitude, "last known")
                            return@addOnSuccessListener
                        }

                        // 3) If still null/null, request ONE update (best fix for emulator)
                        Log.d(TAG, "current=null and last=null -> requesting single update")
                        requestSingleUpdate(onSuccess, onError)
                    }
                    .addOnFailureListener { ex ->
                        Log.e(TAG, "lastLocation failed", ex)
                        onError("lastLocation failed: ${ex.message}")
                    }
            }
            .addOnFailureListener { ex ->
                Log.e(TAG, "getCurrentLocation failed", ex)
                onError("getCurrentLocation failed: ${ex.message}")
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleUpdate(
        onSuccess: (lat: Double, lng: Double, source: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdates(1)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                Log.d(TAG, "requestSingleUpdate result: $loc")

                fused.removeLocationUpdates(this)

                if (loc != null) {
                    onSuccess(loc.latitude, loc.longitude, "single update")
                } else {
                    onError("single update returned null. Emulator location may not be set.")
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(TAG, "location availability: ${availability.isLocationAvailable}")
            }
        }

        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { ex ->
                Log.e(TAG, "requestLocationUpdates failed", ex)
                onError("requestLocationUpdates failed: ${ex.message}")
            }


    }

    @SuppressLint("MissingPermission")
    private fun fetchRestaurantName(onResult: (String) -> Unit) {

        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.TYPES
        )

        val request = FindCurrentPlaceRequest.newInstance(placeFields)

        placesClient.findCurrentPlace(request)
            .addOnSuccessListener { response ->

                val restaurant = response.placeLikelihoods
                    .map { it.place }
                    .firstOrNull { place ->
                        place.types?.contains(Place.Type.RESTAURANT) == true
                    }

                if (restaurant != null) {
                    onResult(restaurant.name ?: "Unknown restaurant")
                } else {
                    onResult("No restaurant found nearby")
                }
            }
            .addOnFailureListener {
                onResult("Place lookup failed: ${it.message}")
            }
    }

}
