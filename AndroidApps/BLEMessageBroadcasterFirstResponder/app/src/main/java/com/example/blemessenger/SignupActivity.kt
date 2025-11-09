package com.example.blemessenger

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignupActivity : AppCompatActivity() {
    
    private lateinit var userPrefs: UserPreferences
    private lateinit var nameInput: TextInputEditText
    private lateinit var ageButton: MaterialButton
    private lateinit var heightGroup: RadioGroup
    private lateinit var weightGroup: RadioGroup
    private lateinit var diabetesCheckbox: MaterialCheckBox
    private lateinit var asthmaCheckbox: MaterialCheckBox
    private lateinit var heartCheckbox: MaterialCheckBox
    private lateinit var epilepsyCheckbox: MaterialCheckBox
    private lateinit var allergiesCheckbox: MaterialCheckBox
    private lateinit var otherMedicalInput: TextInputEditText
    private lateinit var submitButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    
    private var selectedAge = 18
    
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
            ageButton = findViewById(R.id.ageButton)
            heightGroup = findViewById(R.id.heightGroup)
            weightGroup = findViewById(R.id.weightGroup)
            diabetesCheckbox = findViewById(R.id.diabetesCheckbox)
            asthmaCheckbox = findViewById(R.id.asthmaCheckbox)
            heartCheckbox = findViewById(R.id.heartCheckbox)
            epilepsyCheckbox = findViewById(R.id.epilepsyCheckbox)
            allergiesCheckbox = findViewById(R.id.allergiesCheckbox)
            otherMedicalInput = findViewById(R.id.otherMedicalInput)
            submitButton = findViewById(R.id.submitButton)
            progressBar = findViewById(R.id.progressBar)
            
            // Set default age
            ageButton.text = "Age: $selectedAge"
        } catch (e: Exception) {
            android.util.Log.e("SignupActivity", "Error initializing views", e)
            throw e
        }
    }
    
    private fun setupListeners() {
        ageButton.setOnClickListener {
            showAgePicker()
        }
        
        submitButton.setOnClickListener {
            submitForm()
        }
    }
    
    private fun showAgePicker() {
        val ages = (1..120).toList().toTypedArray()
        val agesStrings = ages.map { it.toString() }.toTypedArray()
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Age")
        builder.setSingleChoiceItems(agesStrings, selectedAge - 1) { dialog, which ->
            selectedAge = ages[which]
            ageButton.text = "Age: $selectedAge"
            dialog.dismiss()
        }
        builder.show()
    }
    
    private fun submitForm() {
        val name = nameInput.text.toString().trim()
        
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }
        
        val height = when (heightGroup.checkedRadioButtonId) {
            R.id.heightBelowAverage -> "below average"
            R.id.heightAboveAverage -> "above average"
            else -> "average"
        }
        
        val weight = when (weightGroup.checkedRadioButtonId) {
            R.id.weightBelowAverage -> "below average"
            R.id.weightAboveAverage -> "above average"
            else -> "average"
        }
        
        // Build medical conditions string
        val medicalList = mutableListOf<String>()
        if (diabetesCheckbox.isChecked) medicalList.add("Diabetes")
        if (asthmaCheckbox.isChecked) medicalList.add("Asthma")
        if (heartCheckbox.isChecked) medicalList.add("Heart Condition")
        if (epilepsyCheckbox.isChecked) medicalList.add("Epilepsy")
        if (allergiesCheckbox.isChecked) medicalList.add("Allergies")
        
        val otherMedical = otherMedicalInput.text.toString().trim()
        if (otherMedical.isNotEmpty()) {
            medicalList.add(otherMedical)
        }
        
        val medical = if (medicalList.isEmpty()) {
            "None"
        } else {
            medicalList.joinToString(", ")
        }
        
        // Get UUID
        val uuid = userPrefs.getOrGenerateUUID()
        
        // Show loading
        showLoading(true)
        
        // Call API
        CoroutineScope(Dispatchers.Main).launch {
            val result = ApiService.signupUser(
                uuid = uuid,
                name = name,
                age = selectedAge,
                height = height,
                weight = weight,
                medical = medical
            )
            
            result.onSuccess {
                // Save to local storage
                userPrefs.saveUserProfile(name, selectedAge, height, weight, medical)
                
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
                    userPrefs.saveUserProfile(name, selectedAge, height, weight, medical)
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
        ageButton.isEnabled = !show
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

