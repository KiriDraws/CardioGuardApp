package com.example.cardioguard

import android.Manifest
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject

class ConsultationsFragment : Fragment() {

    private lateinit var welcomeTextView: TextView
    private lateinit var consultationsTextView: TextView

    companion object {
        private const val REQUEST_PERMISSION_CODE = 1
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

        // Set the welcome message
        welcomeTextView.text = "Pacient, $username\n"

        // Create a JSON object with test data for consultations
        val jsonString = """
            {
                "consultations": "Consultatie la cardiolog pe data de 15 iunie 2023.\nConsultatie la endocrinolog pe data de 22 iulie 2023."
            }
        """

        // Parse the JSON object
        val jsonObject = JSONObject(jsonString)
        val consultations = jsonObject.getString("consultations")

        // Display the consultations
        consultationsTextView.text = consultations

        // Check for permissions
        checkAndRequestPermissions()

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
}
