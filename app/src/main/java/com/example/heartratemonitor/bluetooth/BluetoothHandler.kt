package com.example.heartratemonitor.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BluetoothHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val bluetoothLeScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    private var currentScanCallback: ScanCallback? = null

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startBleScan(onDeviceFound: (ScanResult) -> Unit): Boolean {
        if (!isBluetoothSupported() || !isBluetoothEnabled() || !hasScanPermission()) {
            Log.e("BluetoothHandler", "BLE scan failed: unsupported, disabled, or no permission")
            return false
        }

        if (isScanning) {
            stopBleScan()
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d("BLE", "Found device: ${result.device.name} - ${result.device.address}")
                onDeviceFound(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed with error: $errorCode")
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        currentScanCallback = scanCallback
        isScanning = true

        Log.d("BluetoothHandler", "BLE scan started")
        handler.postDelayed({ stopBleScan() }, 10000)

        return true
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (isScanning && currentScanCallback != null) {
            bluetoothLeScanner?.stopScan(currentScanCallback)
            Log.d("BluetoothHandler", "BLE scan stopped")
            isScanning = false
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
