package com.example.gpstest

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), LocationListener {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val TAG = "GPSLocationSender"
    }

    // Variables de UI
    private lateinit var etServerIP: EditText
    private lateinit var etTcpPort: EditText
    private lateinit var etUdpPort: EditText
    private lateinit var btnSendTcp: Button
    private lateinit var btnSendUdp: Button
    private lateinit var btnStartGPS: Button
    private lateinit var btnStopGPS: Button
    private lateinit var tvLocation: TextView
    private lateinit var tvStatus: TextView

    // Variables de ubicación
    private lateinit var locationManager: LocationManager
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var isGPSActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupLocationManager()
        setupClickListeners()

        // Valores por defecto para testing
        etServerIP.setText("192.168.1.100") // Cambia por tu IP
        etTcpPort.setText("8080")
        etUdpPort.setText("8081")
    }

    private fun initializeViews() {
        etServerIP = findViewById(R.id.etServerIP)
        etTcpPort = findViewById(R.id.etTcpPort)
        etUdpPort = findViewById(R.id.etUdpPort)
        btnSendTcp = findViewById(R.id.btnSendTcp)
        btnSendUdp = findViewById(R.id.btnSendUdp)
        btnStartGPS = findViewById(R.id.btnStartGPS)
        btnStopGPS = findViewById(R.id.btnStopGPS)
        tvLocation = findViewById(R.id.tvLocation)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupLocationManager() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    private fun setupClickListeners() {
        btnStartGPS.setOnClickListener {
            startLocationUpdates()
        }

        btnStopGPS.setOnClickListener {
            stopLocationUpdates()
        }

        btnSendTcp.setOnClickListener {
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                sendLocationViaTCP()
            } else {
                showToast("No hay ubicación GPS disponible")
            }
        }

        btnSendUdp.setOnClickListener {
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                sendLocationViaUDP()
            } else {
                showToast("No hay ubicación GPS disponible")
            }
        }
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermissions()) {
            requestLocationPermissions()
            return
        }

        try {
            // Verificar que GPS esté disponible
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showToast("GPS no está habilitado")
                updateStatus("GPS deshabilitado")
                return
            }

            // Solicitar actualizaciones de ubicación
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L, // 2 segundos
                5f,    // 5 metros
                this
            )

            isGPSActive = true
            updateStatus("GPS iniciado, esperando ubicación...")
            showToast("Iniciando GPS...")

            Log.d(TAG, "GPS iniciado correctamente")

        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al iniciar GPS", e)
            updateStatus("Error de permisos")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar GPS", e)
            updateStatus("Error al iniciar GPS")
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(this)
            isGPSActive = false
            updateStatus("GPS detenido")
            showToast("GPS detenido")
            Log.d(TAG, "GPS detenido")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al detener GPS", e)
        }
    }

    // Implementación de LocationListener
    override fun onLocationChanged(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude

        val locationText = """
            Latitud: $currentLatitude
            Longitud: $currentLongitude
            Precisión: ${location.accuracy}m
            Altitud: ${location.altitude}m
            Velocidad: ${location.speed} m/s
            Tiempo: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}
        """.trimIndent()

        tvLocation.text = locationText
        updateStatus("Ubicación actualizada")

        Log.d(TAG, "Nueva ubicación - Lat: $currentLatitude, Lon: $currentLongitude")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "Proveedor habilitado: $provider")
        updateStatus("$provider habilitado")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "Proveedor deshabilitado: $provider")
        updateStatus("$provider deshabilitado")
    }

    @Deprecated("Deprecated in API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "Estado del proveedor $provider cambió a: $status")
    }

    private fun sendLocationViaTCP() {
        val serverIP = etServerIP.text.toString().trim()
        val port = etTcpPort.text.toString().toIntOrNull() ?: 8080

        if (serverIP.isEmpty()) {
            showToast("Introduce la IP del servidor")
            return
        }

        // Usar corrutina para operación de red
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(serverIP, port), 5000) // 5 segundos timeout

                val gpsData = createGPSDataJSON()
                val message = "GPS_TCP_DATA:$gpsData"

                // Enviar datos
                val output = PrintWriter(socket.getOutputStream(), true)
                output.println(message)

                // Intentar leer respuesta (opcional)
                try {
                    val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val response = input.readLine()
                    Log.d(TAG, "Respuesta TCP: $response")
                } catch (e: Exception) {
                    Log.w(TAG, "No se recibió respuesta TCP o timeout")
                }

                socket.close()

                withContext(Dispatchers.Main) {
                    updateStatus("Datos TCP enviados correctamente")
                    showToast("Datos enviados por TCP")
                }

                Log.d(TAG, "Datos TCP enviados a $serverIP:$port - $message")

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Timeout conectando por TCP a $serverIP:$port", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Error TCP: Timeout de conexión")
                    showToast("Error TCP: Timeout")
                }
            } catch (e: ConnectException) {
                Log.e(TAG, "No se pudo conectar por TCP a $serverIP:$port", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Error TCP: No se pudo conectar")
                    showToast("Error TCP: Conexión rechazada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando datos por TCP", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Error TCP: ${e.message}")
                    showToast("Error TCP")
                }
            }
        }
    }

    private fun sendLocationViaUDP() {
        val serverIP = etServerIP.text.toString().trim()
        val port = etUdpPort.text.toString().toIntOrNull() ?: 8081

        if (serverIP.isEmpty()) {
            showToast("Introduce la IP del servidor")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 5000 // 5 segundos timeout

                val gpsData = createGPSDataJSON()
                val message = "GPS_UDP_DATA:$gpsData"
                val data = message.toByteArray()

                val address = InetAddress.getByName(serverIP)
                val packet = DatagramPacket(data, data.size, address, port)

                socket.send(packet)

                // Intentar recibir confirmación (opcional)
                try {
                    val buffer = ByteArray(1024)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)

                    val response = String(responsePacket.data, 0, responsePacket.length)
                    Log.d(TAG, "Respuesta UDP: $response")
                } catch (e: Exception) {
                    Log.w(TAG, "No se recibió respuesta UDP o timeout")
                }

                socket.close()

                withContext(Dispatchers.Main) {
                    updateStatus("Datos UDP enviados correctamente")
                    showToast("Datos enviados por UDP")
                }

                Log.d(TAG, "Datos UDP enviados a $serverIP:$port - $message")

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Timeout enviando UDP a $serverIP:$port", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Error UDP: Timeout")
                    showToast("Error UDP: Timeout")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando datos por UDP", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Error UDP: ${e.message}")
                    showToast("Error UDP")
                }
            }
        }
    }

    private fun createGPSDataJSON(): String {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        return JSONObject().apply {
            put("device_id", deviceId)
            put("latitude", currentLatitude)
            put("longitude", currentLongitude)
            put("timestamp", System.currentTimeMillis())
            put("device_model", android.os.Build.MODEL)
            put("android_version", android.os.Build.VERSION.RELEASE)
            put("app_version", "1.0")
        }.toString()
    }

    private fun checkLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                showToast("Permisos de ubicación denegados")
                updateStatus("Permisos denegados")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            tvStatus.text = "[$timestamp] $status"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isGPSActive) {
            stopLocationUpdates()
        }
    }
}