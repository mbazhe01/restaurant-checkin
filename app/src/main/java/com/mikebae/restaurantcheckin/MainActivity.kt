// File: app/src/main/java/com/mikebae/restaurantcheckin/MainActivity.kt
package com.mikebae.restaurantcheckin

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_LOCATION = 100
    }

    private lateinit var statusText: TextView
    private lateinit var checkInButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkInButton = findViewById(R.id.btnCheckIn)
        statusText = findViewById(R.id.tvStatus)

        statusText.text = "Status: idle"

        checkInButton.setOnClickListener {
            if (!hasLocationPermission()) {
                statusText.text = "Status: requesting location permission..."
                requestLocationPermission()
                return@setOnClickListener
            }

            statusText.text = "Status: getting location (emulator can be slow)..."

            getOneTimeLocation(
                onSuccess = { lat, lng, source ->
                    statusText.text = "Latitude: %.6f\nLongitude: %.6f\n(%s)"
                        .format(lat, lng, source)
                },
                onError = { err ->
                    statusText.text = "Status: $err"
                }
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
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
            if (hasLocationPermission()) {
                statusText.text = "Status: permission granted. Tap Check In."
            } else {
                statusText.text = "Status: permission denied."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getOneTimeLocation(
        onSuccess: (lat: Double, lng: Double, source: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val fused = LocationServices.getFusedLocationProviderClient(this)

        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onSuccess(loc.latitude, loc.longitude, "current")
                    return@addOnSuccessListener
                }

                // Emulator often returns null for "current"; fallback to last known.
                fused.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) {
                            onSuccess(last.latitude, last.longitude, "last known")
                        } else {
                            onError("location unavailable (current=null, last=null). Set emulator location and try again.")
                        }
                    }
                    .addOnFailureListener { ex ->
                        onError("lastLocation failed: ${ex.message}")
                    }
            }
            .addOnFailureListener { ex ->
                onError("getCurrentLocation failed: ${ex.message}")
            }
    }
}
