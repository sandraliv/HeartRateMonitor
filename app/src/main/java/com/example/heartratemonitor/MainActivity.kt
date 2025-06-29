package com.example.heartratemonitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.heartratemonitor.bluetooth.BluetoothClient
import com.example.heartratemonitor.bluetooth.BluetoothHandler
import com.example.heartratemonitor.classes.CustomMarkerView
import com.example.heartratemonitor.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var bluetoothHandler: BluetoothHandler
    @Inject lateinit var bluetoothClient: BluetoothClient
    private lateinit var binding: ActivityMainBinding
    private val heartRateValues = mutableListOf<Float>()

    private val targetDeviceName = "HRSTM"

    // BLE permissions
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            handleBluetoothAccess()
        } else {
            Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Enable Bluetooth if disabled
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothHandler.isBluetoothEnabled()) {
            initiateBleScan()
        } else {
            Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestBluetoothPermissions()

    }

    private fun updateChart(lineChart: LineChart, values: List<Float>) {
        val entries = values.mapIndexed { index, bpm ->
            Entry(index.toFloat(), bpm)
        }

        val marker = CustomMarkerView(this, R.layout.marker_view)
        binding.chart1.marker = marker


        val dataSet = LineDataSet(entries, "Heart Rate")
        dataSet.color = Color.RED
        dataSet.valueTextColor = Color.BLACK
        dataSet.lineWidth = 2f
        dataSet.setDrawCircles(true)
        dataSet.setDrawValues(true)
        dataSet.setDrawHighlightIndicators(true)
        dataSet.highLightColor = Color.BLUE

        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate()
    }



    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            handleBluetoothAccess()
        }
    }

    private fun handleBluetoothAccess() {
        if (!bluetoothHandler.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothHandler.isBluetoothEnabled()) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        } else {
            initiateBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initiateBleScan() {
        bluetoothHandler.startBleScan { result ->
            val scanrecord = result.scanRecord
            val name = scanrecord?.deviceName ?: return@startBleScan
            if (name.equals(targetDeviceName, ignoreCase = true)) {
                Log.d("MainActivity", "MATCH: $name - ${result.device.address}")
                bluetoothHandler.stopBleScan()

                bluetoothClient.connect(
                    device = result.device,
                    onConnected = {
                        Toast.makeText(this, "Connected to $name", Toast.LENGTH_SHORT).show()
                    },
                    onDataReceived = { data ->
                        Log.d("MainActivity", "Data received: $data")
                        binding.HeartRateValue.text = data;
                        if(heartRateValues.size < 20) {
                            val bpm = data.toFloatOrNull()
                            if (bpm != null) {
                                heartRateValues.add(bpm)
                            };
                        }
                        if (heartRateValues.size == 20) {
                            updateChart(binding.chart1, heartRateValues)  // Update chart just once
                        }
                    },
                    onError = { error ->
                        Log.e("MainActivity", "Connection error", error)
                    }
                )
            }
        }
    }
}

