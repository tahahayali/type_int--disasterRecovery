package com.example.firstresponder

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Simplified signup for First Responders - only asks for name
 */
class SignupActivity : AppCompatActivity() {
    
    private lateinit var userPrefs: UserPreferences
    private lateinit var nameInput: TextInputEditText
    private lateinit var submitButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_signup)
            
            userPrefs = UserPreferences(this)
            
            initializeViews()
            setupListeners()
            
        } catch (e: Exception) {
            android.util.Log.e("SignupActivity", "Fatal error in onCreate", e)
            Toast.makeText(this, "Error loading signup: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun initializeViews() {
        try {
            nameInput = findViewById(R.id.nameInput)
            submitButton = findViewById(R.id.submitButton)
            progressBar = findViewById(R.id.progressBar)
        } catch (e: Exception) {
            android.util.Log.e("SignupActivity", "Error initializing views", e)
            throw e
        }
    }
    
    private fun setupListeners() {
        submitButton.setOnClickListener {
            submitForm()
        }
    }
    
    private fun submitForm() {
        val name = nameInput.text.toString().trim()
        
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get UUID
        val uuid = userPrefs.getOrGenerateUUID()
        
        // Show loading
        showLoading(true)
        
        // Call API with type='first_responder' (hidden from user)
        CoroutineScope(Dispatchers.Main).launch {
            val result = ApiService.signupUser(
                uuid = uuid,
                name = name
            )
            
            result.onSuccess {
                // Save to local storage
                userPrefs.saveUserProfile(name)
                
                // Show success and navigate to main
                Toast.makeText(
                    this@SignupActivity,
                    "Registration successful!",
                    Toast.LENGTH_SHORT
                ).show()
                
                navigateToMain()
                
            }.onFailure { error ->
                showLoading(false)
                
                // Check if it's a network error
                if (error.message?.contains("UnknownHost") == true || 
                    error.message?.contains("timeout") == true) {
                    // No internet, save locally and continue
                    Toast.makeText(
                        this@SignupActivity,
                        "No internet. Saved locally.",
                        Toast.LENGTH_LONG
                    ).show()
                    userPrefs.saveUserProfile(name)
                    navigateToMain()
                } else {
                    Toast.makeText(
                        this@SignupActivity,
                        "Error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        submitButton.isEnabled = !show
        nameInput.isEnabled = !show
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}


