package com.example.cardioguard

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONObject

class RecommendationsFragment : Fragment() {

    private lateinit var welcomeTextView: TextView
    private lateinit var recommendationsTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_recommendations, container, false)

        // Initialize the TextViews
        welcomeTextView = view.findViewById(R.id.welcomeTextView)
        recommendationsTextView = view.findViewById(R.id.recommendationsTextView)

        // Retrieve the stored username
        val sharedPreferences: SharedPreferences = requireActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("loggedInUsername", "User")

        // Set the welcome message
        welcomeTextView.text = "Pacient, $username\n"

        // Create a JSON object with test data
        val jsonString = """
            {
                "recommendations": "- Rog a se continua tratamentul igienico sanitar pana la disparitia completa a simptomelor.\n!!! A nu se consuma alcool in timpul tratamentului."
            }
        """

        // Parse the JSON object
        val jsonObject = JSONObject(jsonString)
        val recommendations = jsonObject.getString("recommendations")

        // Display the recommendations
        recommendationsTextView.text = recommendations

        return view
    }
}
