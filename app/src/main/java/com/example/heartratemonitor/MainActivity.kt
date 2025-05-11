package com.example.heartratemonitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.heartratemonitor.bluetooth.BluetoothClient
import com.example.heartratemonitor.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var bluetoothHandler: BluetoothHandler
    private lateinit var binding: ActivityMainBinding
    @Inject lateinit var bluetoothClient: BluetoothClient

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(foundReceiver, filter)
        registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        registerReceiver(uuidReceiver, IntentFilter(BluetoothDevice.ACTION_UUID))

    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(foundReceiver)
        unregisterReceiver(bondReceiver)
        unregisterReceiver(uuidReceiver)

    }


    // Request Bluetooth permissions (SCAN + CONNECT)
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

    // Prompt to enable Bluetooth if disabled
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (bluetoothHandler.isBluetoothEnabled()) {
            Toast.makeText(this, "Bluetooth enabled!", Toast.LENGTH_SHORT).show()
            startDiscovery()
        } else {
            Toast.makeText(this, "Bluetooth not enabled. Cannot start discovery.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestBluetoothPermissions()
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
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothHandler.isBluetoothEnabled()) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
            return
        }
        Toast.makeText(this, "IM HERE NOW", Toast.LENGTH_SHORT).show()

        startDiscovery()
    }

    private fun startDiscovery() {
        val success = bluetoothHandler.startDiscovery()
        if (success) {
            Toast.makeText(this, "Discovery started successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to start Bluetooth discovery", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private val foundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    val name = it.name?.trim() ?: return@let
                    if (name.equals("HRSTM", ignoreCase = true)) {
                        Log.d("BluetoothScan", "MATCH: $name - ${it.address}")

                        // If not already paired, trigger pairing
                        if (it.bondState == BluetoothDevice.BOND_NONE) {
                            Log.d("BluetoothScan", "Attempting to pair with ${it.name}")
                            val paired = it.createBond()
                            Log.d("BluetoothScan", "createBond() called, returned: $paired")

                        } else if (it.bondState == BluetoothDevice.BOND_BONDED) {
                            logDeviceAttributes(it) // already paired, safe to log
                        }
                    }

                }

            }
        }
    }

    @SuppressLint("MissingPermission")
    private val uuidReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_UUID) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val uuidList = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                Log.d("UUIDReceiver", "UUIDs for ${device}, ${uuidList}:")
                if (device != null && uuidList != null) {
                    Log.d("UUIDReceiver", "UUIDs for ${device.name}:")
                    uuidList.forEach {
                        val uuid = (it as? android.os.ParcelUuid)?.uuid
                        Log.d("UUIDReceiver", " - $uuid")
                    }
                } else {
                    Log.d("UUIDReceiver", "UUID broadcast received, but no UUIDs available.")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    (intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
                }

                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                Log.d("BluetoothDebug", "bondReceiver triggered: bondState=$bondState")

                if (device != null && bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d("BluetoothDebug", "bondReceiver triggered: bondState=$bondState")

                    Log.d("Bonding", "Paired successfully with ${device.name}")
                    logDeviceAttributes(device) // NOW it's safe
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    fun logDeviceAttributes(device: BluetoothDevice) {
        Log.d("DeviceInfo", "Name: ${device.name}")
        Log.d("DeviceInfo", "Address: ${device.address}")
        Log.d("DeviceInfo", "Bond State: ${when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> "Bonded"
            BluetoothDevice.BOND_BONDING -> "Bonding"
            BluetoothDevice.BOND_NONE -> "Not Bonded"
            else -> "Unknown"
        }}")

        val btClass = device.bluetoothClass
        Log.d("DeviceInfo", "Device Class: ${btClass.deviceClass}")
        Log.d("DeviceInfo", "Major Device Class: ${btClass.majorDeviceClass}")

        val uuids = device.uuids
        if (uuids != null) {
            for (uuid in uuids) {
                Log.d("DeviceInfo", "UUID: ${uuid.uuid}")
            }
        } else {
            Log.d("DeviceInfo", "No UUIDs available yet")
        }
    }


}
