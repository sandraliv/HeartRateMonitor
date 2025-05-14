package com.example.heartratemonitor.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var gatt: BluetoothGatt? = null
    private var onDataReceived: ((String) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null
    private val readQueue = ArrayDeque<BluetoothGattCharacteristic>()


    // Call this to connect to the BLE device
    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        onConnected: () -> Unit = {},
        onDataReceived: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        disconnect() // Clear any existing connection

        this.onDataReceived = onDataReceived
        this.onError = onError

        Log.d("BluetoothClient", "Connecting to GATT on ${device.name}")
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(
                this.context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            gatt?.close()
            gatt = null
            return
        }
    }

    // Sample write function (requires known characteristic UUID)
    @SuppressLint("MissingPermission")
    fun send(message: String, serviceUUID: UUID, characteristicUUID: UUID) {
        val service = gatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)

        if (characteristic != null) {
            gatt?.readCharacteristic(characteristic)
            val success = gatt?.writeCharacteristic(characteristic)
            Log.d("BluetoothClient", "Write success: $success")
        } else {
            onError?.invoke(IllegalStateException("Characteristic not found"))
        }
    }

    // GATT callback
    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BluetoothClient", "Connected to GATT server")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BluetoothClient", "Disconnected from GATT server")
                onError?.invoke(IOException("Disconnected from GATT"))
            }
        }

        @Deprecated("Deprecated in Java")
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onError?.invoke(IOException("Service discovery failed"))
                return
            }

            Log.d("BluetoothClient", "Services discovered")
            readQueue.clear()

            gatt.services.forEach { service ->
                Log.d("BluetoothClient", "Service UUID: ${service.uuid}")
                service.characteristics.forEach { char ->
                    val props = char.properties

                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        readQueue.add(char)
                    }

                    if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        Log.d("BluetoothClient", "Subscribing to ${char.uuid}")
                        gatt.setCharacteristicNotification(char, true)

                        val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }

            readNextCharacteristic(gatt)
        }


        @SuppressLint("MissingPermission")
        private fun readNextCharacteristic(gatt: BluetoothGatt) {
            val nextChar = readQueue.poll() ?: return
            val result = gatt.readCharacteristic(nextChar)
            Log.d("BluetoothClient", "Reading ${nextChar.uuid}, started: $result")
        }


        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val hex = value.joinToString(" ") { String.format("%02X", it) }
                Log.d("DATA", "Read from ${characteristic.uuid} → $hex")
                onDataReceived?.invoke(hex)
            } else {
                Log.w("BluetoothClient", "Read failed from ${characteristic.uuid}")
            }

            readNextCharacteristic(gatt)
        }


        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val value = characteristic.value
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val hex = value.joinToString(" ") { String.format("%02X", it) }
                Log.d("DATA", "Read from ${characteristic.uuid} → $hex")
                onDataReceived?.invoke(hex)
            } else {
                Log.w("BluetoothClient", "Read failed from ${characteristic.uuid}")
            }

            readNextCharacteristic(gatt)
        }



        // For API < 33
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value
            handleValueFromCharacteristic(characteristic, value)
        }

        // For API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleValueFromCharacteristic(characteristic, value)
        }

        private fun handleValueFromCharacteristic(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val uuid = characteristic.uuid
            if (uuid == UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")) {
                // Heart Rate Measurement
                val flags = value[0].toInt()
                val bpm = if (flags and 0x01 == 0) {
                    value[1].toInt() and 0xFF // 8-bit BPM
                } else {
                    ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF) // 16-bit BPM
                }

                Log.d("HeartRate", "Received BPM: $bpm")
                CoroutineScope(Dispatchers.Main).launch {
                    onDataReceived?.invoke(bpm.toString())
                }
            } else {
                // Fallback for other characteristics (e.g. UTF-8 text)
                val decoded = value.toString(Charsets.UTF_8)
                Log.d("BLE", "Received data (non-HR): $decoded")

                CoroutineScope(Dispatchers.Main).launch {
                    onDataReceived?.invoke(decoded)
                }
            }
        }


    }
}
