package com.example.cardioguard

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPreferences = getSharedPreferences("loginPrefs", MODE_PRIVATE)
        val accountType = sharedPreferences.getString("accountType", "")

        // Setup bottom navigation
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (accountType == "Medic") {
            bottomNavigationView.menu.findItem(R.id.navigation_management_table).isVisible = true
        } else {
            bottomNavigationView.menu.findItem(R.id.navigation_management_table).isVisible = false
        }
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    openFragment(HomeFragment())
                    true
                }
                R.id.navigation_recommendations -> {
                    openFragment(RecommendationsFragment())
                    true
                }
                R.id.navigation_consultations -> {
                    openFragment(ConsultationsFragment())
                    true
                }
                R.id.navigation_management_table -> {
                    if (accountType == "Medic") {
                        openFragment(ManagementTableFragment())
                        true
                    } else {
                        Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
                else -> false
            }
        }

        // Open the default fragment
        if (savedInstanceState == null) {
            openFragment(HomeFragment())
        }
    }

    private fun openFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}
