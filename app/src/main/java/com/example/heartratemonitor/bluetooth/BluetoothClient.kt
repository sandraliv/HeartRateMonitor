package com.example.heartratemonitor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
//Connects to a paired Bluetooth device, runs read/write on background thread, provides clean callbacks
//and handles proper resource cleanup
//It uses the classic Bluetooth RFCOMM Socket (similar to a serial port)
class BluetoothClient @Inject constructor() {

    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null
    private var inputStream: InputStream? = null

    //This is the standard UUID for SPP (Serial Port profile) and is used by most Bluetooth serial devices
    private val sppUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Attempts to connect to the given Bluetooth device
     * Connects using the UUUID and starts reading from inputStream on a background thread
     * Calls: inConnected() when connection succeds, onDataReceived(data: String) every time a new message arrives
     * and onError(e) on failure.
     * This isolates all connection logic in one place, making it easy to call from an Activity or ViewModel.
     */
    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        onConnected: () -> Unit = {},
        onDataReceived: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        disconnect()

        try {
            Log.d("BluetoothClient", "Connecting to device: ${device.name} (${device.address})")
            socket = device.createRfcommSocketToServiceRecord(sppUUID)
            socket?.connect()
            inputStream = socket?.inputStream

            Log.d("BluetoothClient", "Connected to device: ${device.name}")
            onConnected()

            // Start reading in background
            //This uses Kotlin coroutine to continuously read incoming bytes and converts the received bytes to a String
            //Sends it back to the caller on the main thread.
            //Can parse binary protocols, JSON or delimited text.
            readJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(1024)
                while (isActive) {
                    try {
                        val bytes = inputStream?.read(buffer)
                        if (bytes != null && bytes > 0) {
                            val received = String(buffer, 0, bytes)
                            withContext(Dispatchers.Main) {
                                onDataReceived(received)
                            }
                        }
                    } catch (e: IOException) {
                        withContext(Dispatchers.Main) {
                            onError(e)
                        }
                        break
                    }
                }
            }

        } catch (e: IOException) {
            Log.e("BluetoothClient", "Connection failed", e)
            onError(e)
        }
    }

    /**
     * Disconnects from the current device and stops reading.
     * Cancels the coroutine
     * Closes streams and the socket
     * Should always call disconnect() in onStop() or onDestroy() to release resources
     */
    private fun disconnect() {
        readJob?.cancel()
        inputStream?.close()
        socket?.close()
        socket = null
        inputStream = null
    }

    /**
     * Writes a message to the connected device, to its output stream.
     * Useful if the device expects commands or acknowledgements
     */
    fun send(message: String) {
        try {
            socket?.outputStream?.write(message.toByteArray())
            socket?.outputStream?.flush()
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Send failed", e)
        }
    }
}
