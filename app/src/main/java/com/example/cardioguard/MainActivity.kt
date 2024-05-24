package com.example.cardioguard

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject


class MainActivity : Activity() {

    private val DEVICE_ADDRESS = "00:23:02:34:DC:96"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val REQUEST_ENABLE_BLUETOOTH = 1
    private val REQUEST_PERMISSION_BLUETOOTH = 2
    private val READ_DELAY_MILLISECONDS = 2000L // 2 seconds delay

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var inputStream: InputStream? = null

    private lateinit var buttonConnect: Button
    private lateinit var textStatus: TextView
    private lateinit var ekgData: TextView

    private val handler = Handler()
    private val readRunnable = object : Runnable {
        override fun run() {
            readDataFromBluetooth()
            handler.postDelayed(this, READ_DELAY_MILLISECONDS)
        }
    }

    private var pulseValues = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonConnect = findViewById(R.id.buttonConnect)
        textStatus = findViewById(R.id.textStatus)
        ekgData = findViewById(R.id.ekgData)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        buttonConnect.setOnClickListener {
            if (checkBluetoothPermission()) {
                checkBluetoothEnabled()
            } else {
                requestBluetoothPermission()
            }
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH),
            REQUEST_PERMISSION_BLUETOOTH
        )
    }

    private fun checkBluetoothEnabled() {
        try {
            if (bluetoothAdapter == null) {
                // Bluetooth is not supported on this device
                textStatus.text = "Bluetooth not supported"
            } else if (!bluetoothAdapter!!.isEnabled) {
                // Bluetooth is not enabled, request to enable it
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_ADMIN),
                    REQUEST_ENABLE_BLUETOOTH
                )
            } else {
                // Bluetooth is enabled, proceed with connecting to the device
                connectToDevice()
            }
        } catch (e: SecurityException) {
            // Handle SecurityException, usually occurs when permission is denied at runtime
            textStatus.text = "Permission denied: Bluetooth"
        }
    }

    private fun connectToDevice() {
        bluetoothDevice = bluetoothAdapter?.getRemoteDevice(DEVICE_ADDRESS)

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(MY_UUID)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothSocket?.connect()
            textStatus.text = "Status: Connected"
            inputStream = bluetoothSocket?.inputStream
            startReadingData()
        } catch (e: IOException) {
            textStatus.text = "Status: Connection Failed"
            e.printStackTrace()
            try {
                bluetoothSocket?.close()
            } catch (closeException: IOException) {
                closeException.printStackTrace()
            }
        }
    }

    private fun startReadingData() {
        handler.postDelayed(readRunnable, READ_DELAY_MILLISECONDS)
    }

    private fun readDataFromBluetooth() {
        val buffer = ByteArray(1024)
        val bytes: Int
        val dataBuilder = StringBuilder()
        try {
            bytes = inputStream?.read(buffer) ?: 0
            val data = String(buffer, 0, bytes)
            dataBuilder.append(data)
            // Append received data to the builder

            // Split the data by newline character
            val newData = dataBuilder.toString().split("\n")

            // Add the new data to pulseValues
            pulseValues.addAll(newData)

            // Check if we have enough data to form a group of 10
            while (pulseValues.size >= 10) {
                // Take the first 10 values
                val group = pulseValues.subList(0, 1)

                // Process the group (for example, display it or do something else)
                processPulseGroup(group)

                // Remove the processed values from the list
                pulseValues = pulseValues.subList(1, pulseValues.size)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun processPulseGroup(group: List<String>) {
        // Here, you can do whatever you need with the group of 10 pulse values
        // For example, you can update the UI to display them

        val lastTwoChars = group.last().takeLast(3)
        // Display last two characters
        val lastTwoCharsText = "Pulse: $lastTwoChars"
        ekgData.text = lastTwoCharsText

        // Send pulse data to the server
        sendPulseDataToServer(lastTwoChars)

    }
    private fun sendPulseDataToServer(pulse: String) {
        val urlString = "http://localhost:3000/pulseData"  // replace with your server URL

        Thread {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true

                val jsonInputString = JSONObject()
                jsonInputString.put("pulse", pulse)

                connection.outputStream.use { os ->
                    val input = jsonInputString.toString().toByteArray()
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                println("POST Response Code :: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) { // success
                    println("POST request worked")
                } else {
                    println("POST request did not work")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_PERMISSION_BLUETOOTH -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkBluetoothEnabled()
                } else {
                    // Permission denied by the user
                    textStatus.text = "Permission denied: Bluetooth"
                }
            }
            REQUEST_ENABLE_BLUETOOTH -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connectToDevice()
                } else {
                    // Permission denied by the user
                    textStatus.text = "Permission denied: Enable Bluetooth"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(readRunnable)
        try {
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
