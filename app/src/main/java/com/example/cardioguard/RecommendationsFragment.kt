package com.example.cardioguard

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RecommendationsFragment : Fragment() {

    private lateinit var welcomeTextView: TextView
    private lateinit var recommendationsTextView: TextView

    companion object {
        private const val TAG = "RecommendationsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_recommendations, container, false)

        // Initialize the TextViews
        welcomeTextView = view.findViewById(R.id.welcomeTextView)
        recommendationsTextView = view.findViewById(R.id.recommendationsTextView)

        // Retrieve the stored username and token
        val sharedPreferences: SharedPreferences = requireActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("loggedInUsername", "User")
        val token = sharedPreferences.getString("token", null)

        // Set the welcome message
        welcomeTextView.text = "Pacient, $username\n"

        // Fetch recommendations if token is available
        if (token != null) {
            FetchRecommendationsTask(token).execute()
        } else {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private inner class FetchRecommendationsTask(private val token: String) : AsyncTask<Void, Void, String?>() {

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
                displayRecommendations(result)
            } else {
                Toast.makeText(requireContext(), "Failed to fetch recommendations", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayRecommendations(consultationsJson: String) {
        try {
            val jsonObject = JSONObject(consultationsJson)
            val consultationsArray = jsonObject.getJSONArray("data")
            val recommendationsList = StringBuilder()

            for (i in 0 until consultationsArray.length()) {
                val consultation = consultationsArray.getJSONObject(i)
                val recommendations = consultation.getString("recommendations")
                recommendationsList.append("Recommendations: $recommendations\n\n")
            }

            recommendationsTextView.text = recommendationsList.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error parsing consultations data: ${e.message}")
            Toast.makeText(requireContext(), "Error parsing consultations data", Toast.LENGTH_SHORT).show()
        }
    }
}
