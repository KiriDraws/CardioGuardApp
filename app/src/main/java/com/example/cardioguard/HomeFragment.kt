package com.example.cardioguard

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
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

    // Declarații pentru elementele UI
    private lateinit var buttonConnect: Button
    private lateinit var buttonSimulate: Button
    private lateinit var textStatus: TextView
    private lateinit var ekgData: TextView
    private lateinit var ekgGif: ImageView
    private lateinit var pulseDataTextView: TextView
    private lateinit var apiResponseTextView: TextView

    // Constante pentru conexiunea Bluetooth
    private val DEVICE_ADDRESS = "00:23:02:34:DC:96" // Adresa dispozitivului Bluetooth
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID pentru conexiunea Bluetooth
    private val REQUEST_ENABLE_BLUETOOTH = 1 // Cod de solicitare pentru activarea Bluetooth
    private val REQUEST_PERMISSION_BLUETOOTH = 2 // Cod de solicitare pentru permisiuni Bluetooth
    private val READ_DELAY_MILLISECONDS = 4000L // 4 secunde întârziere pentru citire

    // Variabile legate de Bluetooth
    private var bluetoothAdapter: BluetoothAdapter? = null // Adaptorul Bluetooth
    private var bluetoothSocket: BluetoothSocket? = null // Socketul Bluetooth
    private var bluetoothDevice: BluetoothDevice? = null // Dispozitivul Bluetooth
    private var inputStream: InputStream? = null // Fluxul de intrare

    // Handler pentru gestionarea task-urilor repetate
    private val handler = Handler()
    private val readRunnable = object : Runnable {
        override fun run() {
            readDataFromBluetooth() // Citirea datelor de la Bluetooth
        }
    }

    private var dataSent = false // Flag pentru a urmări trimiterea datelor
    private var userToken: String? = null // Tokenul utilizatorului pentru autentificare
    private val ekgValuesList = mutableListOf<String>() // Listă pentru valorile EKG
    private var pulseValues = mutableListOf<String>() // Listă pentru valorile pulsului
    private var startTime: Long = 0 // Timpul de start
    private var pulseIndex = 0 // Indexul pentru valorile pulsului
    private val collectedPulseData = mutableListOf<String>() // Listă pentru valorile colectate ale pulsului
    private var isSimulating = false // Flag pentru simulare

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflați layout-ul pentru acest fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Inițializați elementele UI
        buttonConnect = view.findViewById(R.id.buttonConnect)
        buttonSimulate = view.findViewById(R.id.buttonSimulate)
        textStatus = view.findViewById(R.id.textStatus)
        ekgData = view.findViewById(R.id.ekgData)
        ekgGif = view.findViewById(R.id.ekgGif)
        pulseDataTextView = view.findViewById(R.id.pulseData)
        apiResponseTextView = view.findViewById(R.id.apiResponse)

        // Obțineți adaptorul Bluetooth implicit
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Setează listener-ul pentru click pe butonul de conectare
        buttonConnect.setOnClickListener {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                // Dacă Bluetooth-ul nu este conectat, verificați permisiunile și conectați-vă
                if (checkBluetoothPermission()) {
                    checkBluetoothEnabled()
                } else {
                    requestBluetoothPermission()
                }
            } else {
                // Dacă Bluetooth-ul este conectat, deconectați-vă
                disconnectFromDevice()
            }
        }

        // Setează listener-ul pentru click pe butonul de simulare
        buttonSimulate.setOnClickListener {
            // Comutați simularea la click pe buton
            if (isSimulating) {
                stopSimulation()
            } else {
                startSimulation()
            }
        }

        // Obțineți token-ul utilizatorului din SharedPreferences
        val sharedPreferences = requireActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
        userToken = sharedPreferences.getString("token", null)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Elimină callback-urile și închide fluxurile și socket-urile la distrugerea view-ului
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
        // Verificați dacă permisiunea pentru Bluetooth este acordată
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermission() {
        // Solicită permisiunea pentru Bluetooth dacă nu este acordată
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.BLUETOOTH),
            REQUEST_PERMISSION_BLUETOOTH
        )
    }

    private fun checkBluetoothEnabled() {
        try {
            if (bluetoothAdapter == null) {
                // Bluetooth nu este suportat pe acest dispozitiv
                textStatus.text = "Bluetooth not supported"
            } else if (!bluetoothAdapter!!.isEnabled) {
                // Bluetooth nu este activat, sfătuiți utilizatorul să-l activeze
                Toast.makeText(requireContext(), "Please turn on Bluetooth", Toast.LENGTH_SHORT).show()
                // Opțional, solicitați activarea Bluetooth programatic
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
            } else {
                // Bluetooth este activat, continuați cu conectarea la dispozitiv
                connectToDevice()
            }
        } catch (e: SecurityException) {
            // Gestionați SecurityException, apare de obicei când permisiunea este refuzată la runtime
            textStatus.text = "Permission denied: Bluetooth"
        }
    }

    private fun connectToDevice() {
        // Obțineți dispozitivul Bluetooth folosind adresa
        bluetoothDevice = bluetoothAdapter?.getRemoteDevice(DEVICE_ADDRESS)

        try {
            // Creați un socket pentru a vă conecta la dispozitiv
            bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect() // Conectați-vă la socket
            textStatus.text = "Status: Connected"
            buttonConnect.text = "Disconnect"
            ekgGif.visibility = View.VISIBLE
            // Încărcați GIF-ul folosind Glide
            Glide.with(this).asGif().load(R.drawable.ekg).into(ekgGif)
            inputStream = bluetoothSocket?.inputStream // Obțineți fluxul de intrare
            startReadingData() // Începeți citirea datelor
        } catch (e: IOException) {
            textStatus.text = "Status: Connection Failed"
            e.printStackTrace()
            try {
                bluetoothSocket?.close() // Închideți socket-ul în caz de eroare
            } catch (closeException: IOException) {
                closeException.printStackTrace()
            }
        }
    }

    private fun disconnectFromDevice() {
        try {
            // Elimină callback-urile și închide fluxurile și socket-urile
            handler.removeCallbacks(readRunnable)
            inputStream?.close()
            bluetoothSocket?.close()
            bluetoothSocket = null
            textStatus.text = "Status: Disconnected"
            buttonConnect.text = "Connect"
            ekgGif.visibility = View.GONE
            dataSent = false  // Resetați flag-ul la deconectare
            ekgValuesList.clear() // Goliți lista la deconectare
            pulseValues.clear() // Goliți lista valorilor pulsului la deconectare
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
