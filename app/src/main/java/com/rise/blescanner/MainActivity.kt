package com.rise.blescanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rise.blescanner.databinding.ActivityMainBinding
import com.rise.blescanner.models.RecordingInfo
import com.rise.blescanner.store.ConfigStore
import com.rise.blescanner.store.RecordStore
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private lateinit var configStore: ConfigStore
    private lateinit var sensorManager: SensorManager

    private var isRecording = false
    private var dataPointsCount = 0
    private var startTimeMillis = 0L

    private var zones = arrayOf("Zone_1", "Zone_2", "Zone_3")
    private var selectedZone: String? = null

    private var imuOptions = arrayOf("Walking", "Standing", "Left Turn", "Right Turn")
    private var selectedIMU = "Standing"

    private var currentAccelX: Float? = null
    private var currentAccelY: Float? = null
    private var currentAccelZ: Float? = null

    private var currentGyroX: Float? = null
    private var currentGyroY: Float? = null
    private var currentGyroZ: Float? = null

    private var currentBle1Rssi: Int? = null
    private var currentBle2Rssi: Int? = null
    private var currentBle3Rssi: Int? = null

    private var csvFileWriter: java.io.BufferedWriter? = null
    private var currentCsvFile: File? = null
    private var writeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var lastFlushTime = 0L
    private var lastUiUpdateTime = 0L


    var isAccelEnabled = false;
    var isGyroEnabled = false;

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!isRecording) return
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                currentAccelX = x
                currentAccelY = y
                currentAccelZ = z
                binding.txtAccelXAxis.text = String.format(Locale.US, "%.6f", x)
                binding.txtAccelYAxis.text = String.format(Locale.US, "%.6f", y)
                binding.txtAccelZAxis.text = String.format(Locale.US, "%.6f", z)
                binding.visualizerAccel.addValues(x, y, z)
            } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                currentGyroX = x
                currentGyroY = y
                currentGyroZ = z
                binding.txtGyroXAxis.text = String.format(Locale.US, "%.6f", x)
                binding.txtGyroYAxis.text = String.format(Locale.US, "%.6f", y)
                binding.txtGyroZAxis.text = String.format(Locale.US, "%.6f", z)
                binding.visualizerGyro.addValues(x, y, z)
            }
            if (isAccelEnabled || isGyroEnabled) {
                if (currentBle1Rssi != null && currentBle2Rssi != null && currentBle3Rssi != null) {
                    binding.waitingText.visibility = View.INVISIBLE
                    binding.txtRecordingStatus.text = "RECORDING"
                    writeTelemetryRow()
                } else {
                    binding.waitingText.visibility = View.VISIBLE
                    binding.txtRecordingStatus.text = "WAITING"
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val elapsed = System.currentTimeMillis() - startTimeMillis
                val hours = (elapsed / 3600000)
                val minutes = (elapsed % 3600000) / 60000
                val seconds = (elapsed % 60000) / 1000
                binding.txtRecordingDuration.text =
                    String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            checkBluetoothHardwareAndState()
        } else {
            showFatalDialog(
                "Permissions Required",
                "This app requires Bluetooth and Location permissions to record telemetry data."
            ) {
                finish()
            }
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkLocationHardwareAndState()
        } else {
            showFatalDialog(
                "Bluetooth Required",
                "Bluetooth must be enabled to execute scanning operations."
            ) {
                checkBluetoothHardwareAndState()
            }
        }
    }

    private val locationEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isLocationEnabled()) {
            initializeApplicationLogic()
        } else {
            showFatalDialog(
                "Location Required",
                "Location Services (GPS) must be enabled to execute scanning operations."
            ) {
                checkLocationHardwareAndState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        configStore = ConfigStore(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        checkAndRequestPermissions()
        setupUI()
        binding.visualizerGyro.setMaxValue(6f)
    }

    override fun onResume() {
        super.onResume()
        displayConfiguredAnchors()
        setupZoneSelector()
        setupIMUSelector()

        isAccelEnabled = configStore.getRecordAccel()
        isGyroEnabled = configStore.getRecordGyro()
        val showVisualizer = configStore.getShowVisualizer()

        binding.layoutAccelVisual.visibility = if (isAccelEnabled) View.VISIBLE else View.GONE
        binding.layoutGyroVisual.visibility = if (isGyroEnabled) View.VISIBLE else View.GONE
        binding.layoutSensorVisuals.visibility =
            if (showVisualizer && (isAccelEnabled || isGyroEnabled)) View.VISIBLE else View.GONE
    }

    private fun setupUI() {
        updateRecordingUiState()
        binding.btnToggleRecording.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        binding.btnSettings.setOnClickListener {
            showSettings()
        }
        binding.viewRecordings.setOnClickListener {
            if (isRecording) {
                Toast.makeText(this, "Stop recording before viewing history", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            startActivity(Intent(this, RecordManager::class.java))
        }
    }

    private fun updateRecordingUiState() {
        if (isRecording) {
            binding.btnToggleRecording.text = "STOP RECORDING"
            binding.btnToggleRecording.setIconResource(R.drawable.ic_stop)
            binding.btnToggleRecording.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336"))
            binding.txtRecordingStatus.text = "RECORDING"
            binding.txtRecordingStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.btnToggleRecording.text = "START RECORDING"
            binding.btnToggleRecording.setIconResource(R.drawable.ic_play_arrow)
            binding.btnToggleRecording.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this,
                        R.color.primary_green
                    )
                )
            binding.txtRecordingStatus.text = "IDLE"
            binding.txtRecordingStatus.setTextColor(Color.parseColor("#6E726E"))
        }
    }

    private fun displayConfiguredAnchors() {
        val b1 = configStore.getBle1()
        val b2 = configStore.getBle2()
        val b3 = configStore.getBle3()

        binding.txtBle1Mac.text = b1?.let { "BLE 1 : ${it.macAddress}" } ?: "BLE1: NOT CONFIGURED"
        binding.txtBle2Mac.text = b2?.let { "BLE 2 : ${it.macAddress}" } ?: "BLE2: NOT CONFIGURED"
        binding.txtBle3Mac.text = b3?.let { "BLE 3 : ${it.macAddress}" } ?: "BLE3: NOT CONFIGURED"
    }

    private fun showSettings() {
        if (isRecording) {
            Toast.makeText(this, "Stop recording before changing settings", Toast.LENGTH_SHORT)
                .show()
            return
        }
        startActivity(Intent(this, SettingActivity::class.java))
    }

    private fun startRecording() {
        val b1 = configStore.getBle1()
        val b2 = configStore.getBle2()
        val b3 = configStore.getBle3()

        if (b1 == null || b2 == null || b3 == null) {
            Toast.makeText(this, "Configure all 3 anchors in settings first", Toast.LENGTH_LONG)
                .show()
            return
        }

        if (scanner == null) {
            Toast.makeText(this, "Bluetooth scanner initialization failed", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (selectedZone == null) {
            Toast.makeText(this, "Please select a target zone first", Toast.LENGTH_SHORT).show()
            return
        }

        isAccelEnabled = configStore.getRecordAccel()
        isGyroEnabled = configStore.getRecordGyro()

        val hasAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

        if (isAccelEnabled && !hasAccel) {
            AlertDialog.Builder(this)
                .setTitle("Sensor Unavailable")
                .setMessage("Accelerometer is not available on this device. Please disable it in Settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(this, SettingActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (isGyroEnabled && !hasGyro) {
            AlertDialog.Builder(this)
                .setTitle("Sensor Unavailable")
                .setMessage("Gyroscope is not available on this device. Please disable it in Settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(this, SettingActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        try {
            val dir = File(filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            currentCsvFile = File(dir, "BLE_Telemetry_$timestamp.csv")
            if (writeExecutor.isShutdown) {
                writeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
            }
            csvFileWriter = java.io.BufferedWriter(FileWriter(currentCsvFile, true))
            csvFileWriter?.write("TimeStamp,BLE1_RSSI,BLE2_RSSI,BLE3_RSSI,Accel_X,Accel_Y,Accel_Z,Gyro_X,Gyro_Y,Gyro_Z,IMU,Zone\n")
            csvFileWriter?.flush()
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to create CSV file: ${e.message}", Toast.LENGTH_LONG)
                .show()
            return
        }

        isRecording = true
        dataPointsCount = 0
        startTimeMillis = System.currentTimeMillis()

        binding.txtDataPointsCount.text = "0"
        binding.txtRecordingDuration.text = "00:00:00"

        currentBle1Rssi = null
        currentBle2Rssi = null
        currentBle3Rssi = null

        currentAccelX = null
        currentAccelY = null
        currentAccelZ = null
        currentGyroX = null
        currentGyroY = null
        currentGyroZ = null

        binding.txtBle1Rssi.text = "--"
        binding.txtBle2Rssi.text = "--"
        binding.txtBle3Rssi.text = "--"
        binding.txtAccelXAxis.text = "--"
        binding.txtAccelYAxis.text = "--"
        binding.txtAccelZAxis.text = "--"
        binding.txtGyroXAxis.text = "--"
        binding.txtGyroYAxis.text = "--"
        binding.txtGyroZAxis.text = "--"
        binding.txtCurrentZone.text = selectedZone ?: "--"
        binding.txtCurrentIMU.text = selectedIMU
        binding.visualizerAccel.clear()
        binding.visualizerGyro.clear()

        if (isAccelEnabled) {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager.registerListener(
                    sensorListener,
                    accel,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }

        if (isGyroEnabled) {
            val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            if (gyro != null) {
                sensorManager.registerListener(
                    sensorListener,
                    gyro,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }

        updateRecordingUiState()
        handler.post(timerRunnable)

        startBleScan()
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        updateRecordingUiState()
        handler.removeCallbacks(timerRunnable)

        stopBleScan()
        sensorManager.unregisterListener(sensorListener)
        binding.visualizerAccel.clear()
        binding.visualizerGyro.clear()

        binding.txtDataPointsCount.text = dataPointsCount.toString()

        try {
            writeExecutor.submit {
                try {
                    csvFileWriter?.flush()
                    csvFileWriter?.close()
                    csvFileWriter = null
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.get()

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val durationStr = binding.txtRecordingDuration.text.toString()
            val csvLocation = currentCsvFile?.absolutePath ?: ""
            val entryCount = dataPointsCount

            val recordStore = RecordStore(this)
            recordStore.saveRecording(RecordingInfo(dateStr, durationStr, csvLocation, entryCount))

            Toast.makeText(this, "Saved Recording", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error closing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBleScan() {
        val permissionsOk = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionsOk) return

        binding.waitingText.visibility = View.VISIBLE
        binding.txtRecordingStatus.text = "WAITING"

        Log.d("BLE", "Starting BLE scan")
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                if (!isRecording) return

                val address = result.device.address
                val rssi = result.rssi

                val b1 = configStore.getBle1()?.macAddress
                val b2 = configStore.getBle2()?.macAddress
                val b3 = configStore.getBle3()?.macAddress

                var isMatched = false

                when (address) {
                    b1 -> {
                        currentBle1Rssi = rssi
                        binding.txtBle1Rssi.text = "$rssi"
                        isMatched = true
                    }

                    b2 -> {
                        currentBle2Rssi = rssi
                        binding.txtBle2Rssi.text = "$rssi"
                        isMatched = true
                    }

                    b3 -> {
                        currentBle3Rssi = rssi
                        binding.txtBle3Rssi.text = "$rssi"
                        isMatched = true
                    }
                }

                if (isMatched) {
                    if (currentBle1Rssi != null && currentBle2Rssi != null && currentBle3Rssi != null) {
                        binding.waitingText.visibility = View.INVISIBLE
                        binding.txtRecordingStatus.text = "RECORDING"
                        writeTelemetryRow()
                    } else {
                        binding.waitingText.visibility = View.VISIBLE
                        binding.txtRecordingStatus.text = "WAITING"
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed with error: $errorCode")
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            scanner?.startScan(scanCallback)
        }
    }

    private fun stopBleScan() {
        Log.d("BLE", "Stopping BLE scan")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                scanCallback?.let { scanner?.stopScan(it) }
            }
        } else {
            scanCallback?.let { scanner?.stopScan(it) }
        }
        scanCallback = null
    }

    private fun setupZoneSelector() {
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            zones
        )

        binding.spinnerZoneSelector.setAdapter(adapter)

        binding.spinnerZoneSelector.setOnItemClickListener { parent, _, position, _ ->
            selectedZone = parent.getItemAtPosition(position).toString()
        }

    }

    private fun setupIMUSelector() {
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            imuOptions
        )

        binding.spinnerIMUSelector.setAdapter(adapter)
        binding.spinnerIMUSelector.setText("Standing", false)

        binding.spinnerIMUSelector.setOnItemClickListener { parent, _, position, _ ->
            selectedIMU = parent.getItemAtPosition(position).toString()
        }
    }

    private fun formatFloatToPlain(value: Float?): String {
        if (value == null || value.isNaN() || value.isInfinite()) return ""
        return java.math.BigDecimal(value.toString()).toPlainString()
    }

    private fun writeTelemetryRow() {

        if (isAccelEnabled && (currentAccelX == null || currentAccelY == null || currentAccelZ == null || currentAccelX!!.isNaN() || currentAccelY!!.isNaN() || currentAccelZ!!.isNaN())) {
            return
        }

        if (isGyroEnabled && (currentGyroX == null || currentGyroY == null || currentGyroZ == null || currentGyroX!!.isNaN() || currentGyroY!!.isNaN() || currentGyroZ!!.isNaN())) {
            return
        }

        val r1 = currentBle1Rssi?.toString() ?: ""
        val r2 = currentBle2Rssi?.toString() ?: ""
        val r3 = currentBle3Rssi?.toString() ?: ""

        binding.txtCurrentZone.text = selectedZone ?: "--"
        binding.txtCurrentIMU.text = selectedIMU

        val ax = if (isAccelEnabled) formatFloatToPlain(currentAccelX) else ""
        val ay = if (isAccelEnabled) formatFloatToPlain(currentAccelY) else ""
        val az = if (isAccelEnabled) formatFloatToPlain(currentAccelZ) else ""

        val gx = if (isGyroEnabled) formatFloatToPlain(currentGyroX) else ""
        val gy = if (isGyroEnabled) formatFloatToPlain(currentGyroY) else ""
        val gz = if (isGyroEnabled) formatFloatToPlain(currentGyroZ) else ""

        val zoneVal = selectedZone ?: ""
        val currentTime = System.currentTimeMillis()
        val csvRow = "$currentTime,$r1,$r2,$r3,$ax,$ay,$az,$gx,$gy,$gz,$selectedIMU,$zoneVal\n"

        dataPointsCount++

        if (currentTime - lastUiUpdateTime > 300) {
            binding.txtDataPointsCount.text = dataPointsCount.toString()
            lastUiUpdateTime = currentTime
        }

        writeExecutor.execute {
            try {
                csvFileWriter?.write(csvRow)
                val now = System.currentTimeMillis()
                if (now - lastFlushTime > 5000) {
                    csvFileWriter?.flush()
                    lastFlushTime = now
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            checkBluetoothHardwareAndState()
        }
    }

    private fun checkBluetoothHardwareAndState() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            showFatalDialog(
                "Hardware Unsupported",
                "This device does not possess the required Bluetooth hardware configurations."
            ) {
                finish()
            }
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            checkLocationHardwareAndState()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager =
            getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return locationManager?.let {
            it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } ?: false
    }

    private fun checkLocationHardwareAndState() {
        if (!isLocationEnabled()) {
            showFatalDialog(
                "Location Disabled",
                "This app requires Location Services (GPS) to scan for BLE beacons. Please enable it in Settings."
            ) {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                locationEnableLauncher.launch(intent)
            }
        } else {
            initializeApplicationLogic()
        }
    }

    private fun initializeApplicationLogic() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        displayConfiguredAnchors()
    }

    private fun showFatalDialog(title: String, message: String, onDismiss: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .show()
    }

    override fun onDestroy() {
        stopRecording()
        writeExecutor.shutdown()
        super.onDestroy()
    }
}