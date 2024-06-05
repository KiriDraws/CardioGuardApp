package com.example.cardioguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ConsultationsFragment : Fragment() {

    private lateinit var welcomeTextView: TextView
    private lateinit var consultationsTextView: TextView

    companion object {
        private const val REQUEST_PERMISSION_CODE = 1
        private const val TAG = "ConsultationsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_consultations, container, false)

        // Initialize the TextViews
        welcomeTextView = view.findViewById(R.id.welcomeTextView)
        consultationsTextView = view.findViewById(R.id.consultationsTextView)

        // Retrieve the stored username
        val sharedPreferences = requireActivity().getSharedPreferences("loginPrefs", android.content.Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("loggedInUsername", "User")
        val token = sharedPreferences.getString("token", null)

        // Set the welcome message
        welcomeTextView.text = "Pacient, $username\n"

        // Check for permissions
        checkAndRequestPermissions()

        // Fetch consultations if token is available
        if (token != null) {
            FetchConsultationsTask(token).execute()
        } else {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.BLUETOOTH), REQUEST_PERMISSION_CODE)
        } else {
            // Permissions are already granted, perform your actions
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)  // Important to call super

        if (requestCode == REQUEST_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted
                Toast.makeText(requireContext(), "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                // Permission denied
                Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class FetchConsultationsTask(private val token: String) : AsyncTask<Void, Void, String?>() {

        override fun doInBackground(vararg params: Void?): String? {
            try {
                val url = URL("https://api.cardioguard.eu/patient/consultations")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")

                val responseCode = connection.responseCode
                Log.d(TAG, "Response Code: $responseCode")

                return if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Response: $response")
                    response
                } else {
                    val errorResponse = connection.errorStream.bufferedReader().use { it.readText() }
                    Log.e(TAG, "Error Response: $errorResponse")
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Exception: ${e.message}")
                return null
            }
        }

        override fun onPostExecute(result: String?) {
            if (result != null) {
                displayConsultations(result)
            } else {
                Toast.makeText(requireContext(), "Failed to fetch consultations", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayConsultations(consultationsJson: String) {
        try {
            val jsonObject = JSONObject(consultationsJson)
            val consultationsArray = jsonObject.getJSONArray("data")
            val consultationsList = StringBuilder()

            for (i in 0 until consultationsArray.length()) {
                val consultation = consultationsArray.getJSONObject(i)
                consultationsList.append("Date: ${consultation.getString("consultation_date")}\n")
                consultationsList.append("Doctor: ${consultation.getString("from")}\n")

            }

            consultationsTextView.text = consultationsList.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error parsing consultations data: ${e.message}")
            Toast.makeText(requireContext(), "Error parsing consultations data", Toast.LENGTH_SHORT).show()
        }
    }
}
