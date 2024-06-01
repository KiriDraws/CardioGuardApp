package com.example.cardioguard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.util.Log
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.random.Random

class HomeFragment : Fragment() {

    // UI elements
    private lateinit var buttonConnect: Button
    private lateinit var buttonSimulate: Button
    private lateinit var textStatus: TextView
    private lateinit var ekgData: TextView
    private lateinit var ekgGif: ImageView
    private lateinit var pulseDataTextView: TextView
    private lateinit var apiResponseTextView: TextView

    // Constants for Bluetooth connection
    private val DEVICE_ADDRESS = "00:23:02:34:DC:96"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val REQUEST_ENABLE_BLUETOOTH = 1
    private val REQUEST_PERMISSION_BLUETOOTH = 2
    private val READ_DELAY_MILLISECONDS = 4000L // 4 seconds delay

    // Bluetooth-related variables
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var inputStream: InputStream? = null

    // Handler to manage repeated tasks
    private val handler = Handler()
    private val readRunnable = object : Runnable {
        override fun run() {
            readDataFromBluetooth()
        }
    }

    private var dataSent = false  // Flag to track data sending
    private var userToken: String? = null
    private val ekgValuesList = mutableListOf<String>()
    private var pulseValues = mutableListOf<String>()
    private var startTime: Long = 0
    private var pulseIndex = 0
    private val collectedPulseData = mutableListOf<String>()
    private var isSimulating = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize UI elements
        buttonConnect = view.findViewById(R.id.buttonConnect)
        buttonSimulate = view.findViewById(R.id.buttonSimulate)
        textStatus = view.findViewById(R.id.textStatus)
        ekgData = view.findViewById(R.id.ekgData)
        ekgGif = view.findViewById(R.id.ekgGif)
        pulseDataTextView = view.findViewById(R.id.pulseData)
        apiResponseTextView = view.findViewById(R.id.apiResponse)

        // Get the default Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Set up button click listeners
        buttonConnect.setOnClickListener {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                // If Bluetooth is not connected, check permissions and connect
                if (checkBluetoothPermission()) {
                    checkBluetoothEnabled()
                } else {
                    requestBluetoothPermission()
                }
            } else {
                // If Bluetooth is connected, disconnect
                disconnectFromDevice()
            }
        }

        buttonSimulate.setOnClickListener {
            // Toggle simulation on button click
            if (isSimulating) {
                stopSimulation()
            } else {
                startSimulation()
            }
        }

        // Get the user token from SharedPreferences
        val sharedPreferences = requireActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
        userToken = sharedPreferences.getString("token", null)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove callbacks and close streams and sockets on view destruction
        handler.removeCallbacks(readRunnable)
        handler.removeCallbacks(simulateDataRunnable)
        try {
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        // Check if Bluetooth permission is granted
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermission() {
        // Request Bluetooth permission if not granted
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
        // Get the Bluetooth device using the address
        bluetoothDevice = bluetoothAdapter?.getRemoteDevice(DEVICE_ADDRESS)

        try {
            // Create a socket to connect to the device
            bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect()
            textStatus.text = "Status: Connected"
            buttonConnect.text = "Disconnect"
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

    private fun disconnectFromDevice() {
        try {
            // Remove callbacks and close streams and sockets
            handler.removeCallbacks(readRunnable)
            inputStream?.close()
            bluetoothSocket?.close()
            bluetoothSocket = null
            textStatus.text = "Status: Disconnected"
            buttonConnect.text = "Connect"
            ekgGif.visibility = View.GONE
            dataSent = false  // Reset the flag when disconnected
            ekgValuesList.clear() // Clear the list when disconnected
            pulseValues.clear() // Clear the pulse values list when disconnected
        } catch (e: IOException) {
            e.printStackTrace()
            textStatus.text = "Status: Disconnection Failed"
        }
    }

    private fun startReadingData() {
        // Start reading data from Bluetooth
        handler.postDelayed(readRunnable, READ_DELAY_MILLISECONDS)
        startTime = System.currentTimeMillis()
    }

    private fun readDataFromBluetooth() {
        val buffer = ByteArray(1024)
        var bytes: Int
        val dataBuilder = StringBuilder()
        try {
            // Read data from the input stream
            bytes = inputStream?.read(buffer) ?: 0
            val data = String(buffer, 0, bytes)
            dataBuilder.append(data)

            // Split the data by newline character
            val newData = dataBuilder.toString().split("\n")

            // Add the new data to pulseValues
            pulseValues.addAll(newData)

            // Remove the digit '2' and '\r' from each value
            pulseValues = pulseValues.map { it.replace("2", "").replace("\r", "") }.filter { it.isNotBlank() }.toMutableList()

            // Check if we have enough data to form a group of 4
            if (pulseValues.size >= 4) {
                // Take the first 4 values
                val group = pulseValues.subList(0, 4)

                // Process the group (for example, display it or do something else)
                processPulseGroup(group)

                // Remove the processed values from the list
                pulseValues = pulseValues.subList(4, pulseValues.size)
            }

            // Check if 4 seconds have passed to collect and display pulse data
            val currentTime = System.currentTimeMillis()
            if (currentTime - startTime >= 4000L) {
                saveAndDisplayPulseData(pulseValues.take(4).joinToString(", "))
                startTime = currentTime
            }

            // Schedule next read
            handler.postDelayed(readRunnable, READ_DELAY_MILLISECONDS)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun processPulseGroup(group: List<String>) {
        // Format the pulse values and update the UI
        val formattedValues = group.joinToString(", ")
        val pulseText = "Pulse: $formattedValues"
        ekgData.text = pulseText
        sendPulseDataToServer(formattedValues, pulseIndex)
        pulseIndex++
    }

    private fun saveAndDisplayPulseData(pulseValues: String) {
        // Save and display the collected pulse data
        val currentTime = System.currentTimeMillis()
        val formattedDateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentTime))
        val pulseData = if (collectedPulseData.isEmpty()) {
            // For the first set of pulse values, display the timestamp as a title
            "Pulse values read at $formattedDateTime: $pulseValues"
        } else {
            // For subsequent sets of pulse values, only display the pulse values in a single row
            pulseValues
        }
        collectedPulseData.add(pulseData)
        pulseDataTextView.text = collectedPulseData.joinToString(separator = ", ", prefix = "", postfix = "") // Join pulse values with comma
    }

    private fun sendPulseDataToServer(pulse: String, pulseIndex: Int) {
        // Send pulse data to the server
        userToken?.let { token ->
            SendEkgDataTask(token, pulse, "your_recorder_at_data").execute()
        } ?: run {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSimulation() {
        // Start the simulation
        isSimulating = true
        buttonSimulate.text = "Stop Simulator"
        textStatus.text = "Status: Connected (Simulator)"
        ekgGif.visibility = View.VISIBLE
        // Load the GIF using Glide
        Glide.with(this).asGif().load(R.drawable.ekg).into(ekgGif)
        handler.postDelayed(simulateDataRunnable, READ_DELAY_MILLISECONDS)
        startTime = System.currentTimeMillis()
    }

    private fun stopSimulation() {
        // Stop the simulation
        isSimulating = false
        buttonSimulate.text = "Simulate"
        textStatus.text = "Status: Disconnected"
        ekgGif.visibility = View.GONE
        handler.removeCallbacks(simulateDataRunnable)
    }

    private val simulateDataRunnable = object : Runnable {
        override fun run() {
            if (isSimulating) {
                // Generate random pulse values for simulation
                val randomPulseValues = List(4) { Random.nextInt(60, 120).toString() }
                processPulseGroup(randomPulseValues)
                saveAndDisplayPulseData(randomPulseValues.joinToString(", "))

                // Schedule next simulation
                handler.postDelayed(this, READ_DELAY_MILLISECONDS)
            }
        }
    }

    // AsyncTask to send EKG data to the server
    private inner class SendEkgDataTask(
        private val token: String,
        private val formattedValues: String,
        private val recorderAt: String
    ) : AsyncTask<Void, Void, Pair<String?, String?>>() {

        override fun doInBackground(vararg params: Void?): Pair<String?, String?> {
            // Log the values being sent to the server
            Log.d("SendEkgDataTask", "Sending EKG values to server: $formattedValues")

            return try {
                // Set up the connection to the server
                val url = URL("https://api.cardioguard.eu/pacient/ekg")
                val postData = JSONObject().apply {
                    put("ekg_values", formattedValues) // Using formattedValues instead of ekgValues
                    put("recorder_at", recorderAt)
                }.toString()

                // Log the JSON object being sent
                Log.d("SendEkgDataTask", "JSON being sent: $postData")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.doOutput = true

                // Send the JSON data to the server
                connection.outputStream.use { outputStream ->
                    outputStream.write(postData.toByteArray())
                    outputStream.flush()
                }

                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response from the server
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Pair(response, null)
                } else {
                    // Read the error response from the server
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Pair(null, errorResponse)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(null, e.message)
            }
        }

        override fun onPostExecute(result: Pair<String?, String?>) {
            // Display the server response or error
            val (response, error) = result
            if (response != null) {
                apiResponseTextView.text = response
                apiResponseTextView.visibility = TextView.VISIBLE
                Toast.makeText(requireContext(), "EKG data sent successfully", Toast.LENGTH_SHORT).show()
            } else {
                apiResponseTextView.text = error
                apiResponseTextView.visibility = TextView.VISIBLE
                Toast.makeText(requireContext(), "Failed to send EKG data", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
