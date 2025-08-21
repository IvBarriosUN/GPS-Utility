package com.example.gpstest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.telephony.SmsManager
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    var phonenumber: EditText? = null

    // Declare a variable for the FusedLocationProviderClient
    private lateinit var locationClient: FusedLocationProviderClient

    // Declare a TextView to display location data
    private lateinit var locationText: TextView

    // Define a constant for the location permission request code
    private val LOCATION_PERMISSION_REQUEST = 1001

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the TextView and Button from the layout
        locationText = findViewById(R.id.locationText)
        val getLocationBtn = findViewById<Button>(R.id.getLocationBtn)

        // Initialize the location provider client
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        phonenumber = findViewById(R.id.editText)

        // Set a click listener for the button to get the current location
        getLocationBtn.setOnClickListener {
            getCurrentLocation()
        }
    }

    // Function to get the current location
    private fun getCurrentLocation() {
        // Check if the location permission is granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If permission is not granted, request it from the user
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }

        // Fetch the last known location
        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                // If location is available, extract latitude and longitude
                val lat = location.latitude
                val lon = location.longitude
                val tim = location.time

                // Display location in the TextView
                locationText.text = "Latitude: $lat\nLongitude: $lon\nTime: $tim"

                val phoneNumber = phonenumber?.text.toString()
                val smsManager:SmsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, "Latitude: $lat\nLongitude: $lon\nTime: $tim", null, null)

            } else {
                // If location is null, display an error message
                locationText.text = "Unable to get location"
            }
        }
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if the permission was granted
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // If permission is granted, fetch the location
            getCurrentLocation()
        } else {
            // If permission is denied, update the TextView with an error message
            locationText.text = "Location permission denied"
        }
    }
}