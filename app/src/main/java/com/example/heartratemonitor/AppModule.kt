package com.example.heartratemonitor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.*
import javax.inject.Inject

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {
    @Provides
    fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }
}

class BluetoothHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        if (!isLocationEnabled()) {
            Log.e("BluetoothHandler", "Location services are OFF")
            return false
        }
        Log.d("BluetoothHandler", """
        Bluetooth Supported: ${isBluetoothSupported()}
        Bluetooth Enabled: ${isBluetoothEnabled()}
        Permission Granted: ${hasScanPermission()}
        Is Discovering: ${bluetoothAdapter?.isDiscovering}
        Location Enabled: ${isLocationEnabled()}
    """.trimIndent())

        if (!isBluetoothSupported() || !isBluetoothEnabled() || !hasScanPermission()) {
            Log.e("BluetoothHandler", "Start failed: Adapter invalid or permissions missing")
            return false
        }

        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
            Log.d("BluetoothHandler", "Discovery was already running. Cancelling before restarting.")
        }

        val result = bluetoothAdapter?.startDiscovery() == true
        Log.d("BluetoothHandler", "startDiscovery() returned: $result")
        return result
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }


    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (hasScanPermission()) {
            bluetoothAdapter?.cancelDiscovery()
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

