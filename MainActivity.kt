package com.example.finalproject

import android.content.BroadcastReceiver
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.finalproject.R
import java.text.SimpleDateFormat
import java.util.*


import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.finalproject.databinding.ActivityMainBinding
import java.io.File






class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var connectivityBroadcastReceiver: BroadcastReceiver
    private lateinit var batteryBroadcastReceiver: BroadcastReceiver
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var preview: Preview



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_PERMISSIONS
            )
        }

        binding.captureButton.setOnClickListener {
            captureImage()
        }



        textView = findViewById(R.id.textView)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Register BroadcastReceiver to receive connectivity status updates
        connectivityBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateConnectivityStatus()
            }
        }

        // Register BroadcastReceiver to receive battery status updates
        batteryBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateBatteryStatus()
            }
        }

        // Location Listener
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateLocationStatus(location.latitude, location.longitude)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
    }

    override fun onResume() {
        super.onResume()
        val connectivityIntentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityBroadcastReceiver, connectivityIntentFilter)

        val batteryIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryBroadcastReceiver, batteryIntentFilter)

        updateConnectivityStatus() // Update connectivity status initially
        updateBatteryStatus() // Update battery status initially

        requestLocationUpdates() // Request location updates
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(connectivityBroadcastReceiver)
        unregisterReceiver(batteryBroadcastReceiver)
        removeLocationUpdates() // Remove location updates
    }

    private fun updateConnectivityStatus() {
        val activeNetwork: Network? = connectivityManager.activeNetwork
        val capabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected: Boolean = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        val connectivityStatus = if (isConnected) "ON" else "OFF"
        textView.text = "Internet Connectivity Status: $connectivityStatus\n"
    }

    private fun updateBatteryStatus() {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status: Int? = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val batteryPercentage: Int? = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val chargingStatus = if (isCharging) "ON" else "OFF"

        textView.append("Battery Charging Status: $chargingStatus\n")
        textView.append("Battery Percentage: $batteryPercentage%\n")
    }

    private fun requestLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                locationListener,
                null
            )
        } else {
            // Request location permission
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun removeLocationUpdates() {
        locationManager.removeUpdates(locationListener)
    }

    private fun updateLocationStatus(latitude: Double, longitude: Double) {
        textView.append("Location: $latitude, $longitude\n")
    }



    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val lifecycleOwner = this
                val lifecycle = lifecycleOwner.lifecycle
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                preview = Preview.Builder().build()

                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)

                preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            } catch (exc: Exception) {
                // Handle camera setup errors
            }
        }, ContextCompat.getMainExecutor(this))
    }




    private fun captureImage() {
        val imageFile = createImageFile()

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    // Display the captured image
                    binding.capturedImage.setImageURI(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle capture error
                }
            }
        )
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("IMG_${timeStamp}", ".jpg", storageDir)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                // Handle permission not granted error
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}