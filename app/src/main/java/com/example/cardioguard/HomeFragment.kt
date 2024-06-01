package com.example.cardioguard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class HomeFragment : Fragment() {

    private lateinit var buttonConnect: Button
    private lateinit var textStatus: TextView
    private lateinit var ekgData: TextView
    private lateinit var ekgGif: ImageView
    private lateinit var pulseDataTextView: TextView

    private val DEVICE_ADDRESS = "00:23:02:34:DC:96"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val REQUEST_ENABLE_BLUETOOTH = 1
    private val REQUEST_PERMISSION_BLUETOOTH = 2
    private val READ_DELAY_MILLISECONDS = 2000L // 2 seconds delay

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var inputStream: InputStream? = null

    private val handler = Handler()
    private val readRunnable = object : Runnable {
        override fun run() {
            readDataFromBluetooth()
            handler.postDelayed(this, READ_DELAY_MILLISECONDS)
        }
    }

    private var pulseValues = mutableListOf<String>()
    private var collectedPulseData = mutableListOf<String>()
    private var startTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        buttonConnect = view.findViewById(R.id.buttonConnect)
        textStatus = view.findViewById(R.id.textStatus)
        ekgData = view.findViewById(R.id.ekgData)
        ekgGif = view.findViewById(R.id.ekgGif)
        pulseDataTextView = view.findViewById(R.id.pulseData)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        buttonConnect.setOnClickListener {
            if (checkBluetoothPermission()) {
                checkBluetoothEnabled()
            } else {
                requestBluetoothPermission()
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(readRunnable)
        try {
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
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
                    requireActivity(),
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
            bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect()
            textStatus.text = "Status: Connected"
            ekgGif.visibility = View.VISIBLE
            // Load the GIF using Glide
            Glide.with(this).asGif().load(R.drawable.ekg).into(ekgGif)
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
        startTime = System.currentTimeMillis()
    }

    private fun readDataFromBluetooth() {
        val buffer = ByteArray(1024)
        var bytes: Int
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

            // Check if 3 seconds have passed to collect and display pulse data
            val currentTime = System.currentTimeMillis()
            if (currentTime - startTime >= 3000L) {
                saveAndDisplayPulseData()
                startTime = currentTime
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private var pulseIndex = 0

    private fun processPulseGroup(group: List<String>) {
        val lastTwoChars = group.last().takeLast(3)
        val lastTwoCharsText = "Pulse: $lastTwoChars"
        ekgData.text = lastTwoCharsText
        sendPulseDataToServer(lastTwoChars, pulseIndex)
        pulseIndex++
    }

    private fun saveAndDisplayPulseData() {
        val pulseValue = ekgData.text.takeLast(3) // Extract pulse value from ekgData TextView
        val currentTime = System.currentTimeMillis()
        val formattedDateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentTime))
        val pulseData = if (collectedPulseData.isEmpty()) {
            // For the first set of pulse values, display the timestamp as a title
            "Pulse values read at $formattedDateTime: $pulseValue"
        } else {
            // For subsequent sets of pulse values, only display the pulse values in a single row
            "$pulseValue"
        }
        collectedPulseData.add(pulseData)
        pulseDataTextView.text = collectedPulseData.joinToString(separator = ", ", prefix = "", postfix = "") // Join pulse values with comma
    }

    private fun sendPulseDataToServer(pulse: String, index: Int) {
        val urlString = "http://192.168.1.138:3000/pulseData"

        Thread {
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true

                val jsonInputString = JSONObject()
                jsonInputString.put("id", index)
                jsonInputString.put("pulse", pulse)

                connection.outputStream.use { os ->
                    val input = jsonInputString.toString().toByteArray()
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                println("POST Response Code :: $responseCode")

                if (responseCode == java.net.HttpURLConnection.HTTP_OK) { // success
                    println("POST request worked")
                } else {
                    println("POST request did not work, Response Code: $responseCode")
                }

                connection.inputStream.use {
                    val response = it.bufferedReader().use { reader -> reader.readText() }
                    println("Response: $response")
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
