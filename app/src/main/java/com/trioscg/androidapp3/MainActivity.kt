package com.trioscg.androidapp3

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var speedText: TextView
    private lateinit var unitText: TextView
    private lateinit var toggleButton: Button
    private lateinit var startRunButton: Button
    private lateinit var topSpeedText: TextView
    private lateinit var overallTopSpeedText: TextView
    private lateinit var odometerText: TextView
    private lateinit var resetButton: Button

    // State tracking
    private var useMph = false
    private var trackingQuarterMile = false
    private var quarterMileStartLocation: Location? = null
    private var lastLocation: Location? = null
    private var topSpeedDuringRun = 0.0
    private var overallTopSpeed = 0.0
    private var totalDistance = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        speedText = findViewById(R.id.speedText)
        unitText = findViewById(R.id.unitText)
        toggleButton = findViewById(R.id.toggleButton)
        startRunButton = findViewById(R.id.startRunButton)
        topSpeedText = findViewById(R.id.topSpeedText)
        overallTopSpeedText = findViewById(R.id.overallTopSpeedText)
        odometerText = findViewById(R.id.odometerText)
        resetButton = findViewById(R.id.resetButton)

        topSpeedText.visibility = TextView.GONE

        // Set up location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Default unit is KPH
        useMph = false
        updateUnitsDisplay()

        // Toggle units (MPH/KPH)
        toggleButton.setOnClickListener {
            useMph = !useMph
            updateUnitsDisplay()
        }

        // Start 1/4 mile run
        startRunButton.setOnClickListener {
            startQuarterMileRun()
        }

        // Reset button logic
        resetButton.setOnClickListener {
            resetAllStats()
        }

        // Start getting location updates
        checkLocationPermissionAndStart()
    } // onCreate()

    // Update all unit-related labels based on current unit preference
    private fun updateUnitsDisplay() {
        unitText.text = if (useMph) getString(R.string.mph) else getString(R.string.kph)
        toggleButton.text = if (useMph) getString(R.string.switch_to_kph) else getString(R.string.switch_to_mph)

        val convertedOverall = if (useMph) overallTopSpeed * 2.23694 else overallTopSpeed * 3.6
        overallTopSpeedText.text = String.format(getString(R.string.top_speed_label), convertedOverall)

        val convertedTop1320 = if (useMph) topSpeedDuringRun * 2.23694 else topSpeedDuringRun * 3.6
        topSpeedText.text = String.format(getString(R.string.top_speed_1320_label), convertedTop1320)

        val distance = if (useMph) totalDistance * 0.000621371 else totalDistance / 1000
        val unit = if (useMph) "mi" else "km"
        odometerText.text = String.format(getString(R.string.distance_label), distance, unit)
    } // updateUnitsDisplay()

    // Ask for permissions if needed, otherwise start location updates
    private fun checkLocationPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        } else {
            startLocationUpdates()
        }
    } // checkLocationPermissionsAndStart()

    // Start receiving live location updates
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 500L
        ).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return

                    val speedMps = location.speed
                    val speed = if (useMph) speedMps * 2.23694 else speedMps * 3.6
                    speedText.text = String.format(getString(R.string.speed_label), speed)

                    // Track total distance
                    lastLocation?.let {
                        totalDistance += it.distanceTo(location)
                    }
                    lastLocation = location

                    val distance = if (useMph) totalDistance * 0.000621371 else totalDistance / 1000
                    val unit = if (useMph) "mi" else "km"
                    odometerText.text = String.format(getString(R.string.distance_label), distance, unit)

                    // Handle quarter mile tracking
                    if (trackingQuarterMile) {
                        quarterMileStartLocation?.let { startLocation ->
                            val distanceRun = location.distanceTo(startLocation)
                            if (speed > topSpeedDuringRun) {
                                topSpeedDuringRun = speed
                                topSpeedText.text = String.format(getString(R.string.top_speed_1320_label), topSpeedDuringRun)
                            }
                            if (distanceRun >= 402.34f) { // 1320ft = 402.34m
                                trackingQuarterMile = false
                                Toast.makeText(this@MainActivity, getString(R.string.quarter_mile_completed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // Track overall top speed
                    if (speed > overallTopSpeed) {
                        overallTopSpeed = speed
                        overallTopSpeedText.text = String.format(getString(R.string.top_speed_label), overallTopSpeed)
                    }
                }
            },
            Looper.getMainLooper()
        )
    } // startLocationUpdates()

    // Begin a 1320ft (1/4 mile) run
    private fun startQuarterMileRun() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    quarterMileStartLocation = it
                    trackingQuarterMile = true
                    topSpeedDuringRun = 0.0
                    topSpeedText.text = String.format(getString(R.string.top_speed_1320_label), 0.0)
                    topSpeedText.visibility = TextView.VISIBLE
                    Toast.makeText(this, getString(R.string.quarter_mile_started), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    } // startQuarterMileRun()

    // Reset all tracked stats
    private fun resetAllStats() {
        topSpeedDuringRun = 0.0
        overallTopSpeed = 0.0
        totalDistance = 0.0
        lastLocation = null
        quarterMileStartLocation = null
        trackingQuarterMile = false

        speedText.text = getString(R.string.speed_label, 0.0)
        odometerText.text = getString(R.string.distance_label, 0.0, if (useMph) "mi" else "km")
        topSpeedText.text = getString(R.string.top_speed_1320_label, 0.0)
        topSpeedText.visibility = TextView.GONE
        overallTopSpeedText.text = getString(R.string.top_speed_label, 0.0)

        Toast.makeText(this, "All stats reset.", Toast.LENGTH_SHORT).show()
    } // resetAllStats()

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    } // onRequestPermissionResult()
} // MainActivity