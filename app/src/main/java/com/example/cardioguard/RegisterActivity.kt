package com.example.cardioguard

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val editTextLastName = findViewById<EditText>(R.id.editTextLastName)
        val editTextFirstName = findViewById<EditText>(R.id.editTextFirstName)
        val editTextCNP = findViewById<EditText>(R.id.editTextCNP)
        val editTextUsername = findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = findViewById<EditText>(R.id.editTextPassword)
        val editTextConfirmPassword = findViewById<EditText>(R.id.editTextConfirmPassword)
        val radioGroupAccountType = findViewById<RadioGroup>(R.id.radioGroupAccountType)
        val buttonRegister = findViewById<Button>(R.id.buttonRegister)

        buttonRegister.setOnClickListener {
            val lastName = editTextLastName.text.toString()
            val firstName = editTextFirstName.text.toString()
            val cnp = editTextCNP.text.toString()
            val username = editTextUsername.text.toString()
            val password = editTextPassword.text.toString()
            val confirmPassword = editTextConfirmPassword.text.toString()
            val selectedAccountTypeId = radioGroupAccountType.checkedRadioButtonId
            val accountType = when (selectedAccountTypeId) {
                R.id.radioButtonMedic -> "medic"
                R.id.radioButtonPacient -> "patient"
                else -> ""
            }

            Log.d("RegisterActivity", "Register button clicked")
            Log.d("RegisterActivity", "Input values: lastName=$lastName, firstName=$firstName, cnp=$cnp, username=$username, accountType=$accountType")

            if (lastName.isNotEmpty() && firstName.isNotEmpty() && cnp.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() && accountType.isNotEmpty()) {
                if (password == confirmPassword) {
                    Log.d("RegisterActivity", "Starting RegisterTask")
                    Log.d("RegisterActivity", "Account type: $accountType")

                    RegisterTask(lastName, firstName, cnp, username, password, confirmPassword, accountType).execute()
                } else {
                    Log.d("RegisterActivity", "Passwords do not match")
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("RegisterActivity", "Required information is missing")
                Toast.makeText(this, "Please enter all the required information", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class RegisterTask(
        private val lastName: String,
        private val firstName: String,
        private val cnp: String,
        private val username: String,
        private val password: String,
        private val confirmPassword: String,
        private val accountType: String
    ) : AsyncTask<Void, Void, String?>() {

        override fun doInBackground(vararg params: Void?): String? {
            try {
                Log.d("RegisterTask", "Starting registration process")
                val url = URL("https://api.cardioguard.eu/register")
                val postData = "last_name=$lastName&first_name=$firstName&cnp=$cnp&username=$username&password=$password&confirm_password=$confirmPassword&type_of_user=$accountType"
                Log.d("RegisterTask", "Post data: $postData")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                val outputStream: OutputStream = connection.outputStream
                outputStream.write(postData.toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                Log.d("RegisterTask", "Response Code: $responseCode")

                return if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("RegisterTask", "Response: $response")
                    val jsonResponse = JSONObject(response)
                    jsonResponse.getString("token")
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e("RegisterTask", "Error Response: $errorResponse")
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("RegisterTask", "Exception: ${e.message}")
                return null
            }
        }

        override fun onPostExecute(token: String?) {
            Log.d("RegisterTask", "Post execute")
            if (token != null) {
                Log.d("RegisterTask", "Registration successful, token: $token")
                saveToken(token)
                navigateToMainActivity()
            } else {
                Log.e("RegisterTask", "Registration failed")
                Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToken(token: String) {
        val sharedPreferences = getSharedPreferences("loginPrefs", AppCompatActivity.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("token", token)
        editor.apply()
    }

    private fun navigateToMainActivity() {
        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
