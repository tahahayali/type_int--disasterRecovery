package com.example.blemessenger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class EmergencyQuestionnaireActivity : AppCompatActivity() {
    
    private lateinit var questionText: TextView
    private lateinit var progressText: TextView
    private lateinit var yesButton: MaterialButton
    private lateinit var noButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    
    private val questions = arrayOf(
        "Do you need assistance?",
        "Are you alone?",
        "Are you or anybody around you injured/in need of urgent medical care?",
        "Are you stuck?"
    )
    
    private var currentQuestionIndex = 0
    private val answers = BooleanArray(4)
    private var currentAnswer: Boolean? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_questionnaire)
        
        initializeViews()
        setupListeners()
        showQuestion(0)
    }
    
    private fun initializeViews() {
        questionText = findViewById(R.id.questionText)
        progressText = findViewById(R.id.progressText)
        yesButton = findViewById(R.id.yesButton)
        noButton = findViewById(R.id.noButton)
        nextButton = findViewById(R.id.nextButton)
        cancelButton = findViewById(R.id.cancelButton)
    }
    
    private fun setupListeners() {
        yesButton.setOnClickListener {
            selectAnswer(true)
        }
        
        noButton.setOnClickListener {
            selectAnswer(false)
        }
        
        nextButton.setOnClickListener {
            goToNextQuestion()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun showQuestion(index: Int) {
        currentQuestionIndex = index
        questionText.text = questions[index]
        progressText.text = "Question ${index + 1} of ${questions.size}"
        
        // Reset selection
        currentAnswer = null
        resetButtonStates()
        nextButton.isEnabled = false
        
        // Update next button text
        if (index == questions.size - 1) {
            nextButton.text = "START EMERGENCY BROADCAST"
        } else {
            nextButton.text = "NEXT"
        }
    }
    
    private fun selectAnswer(answer: Boolean) {
        currentAnswer = answer
        answers[currentQuestionIndex] = answer
        
        // Update button states
        if (answer) {
            // YES selected
            yesButton.alpha = 1.0f
            noButton.alpha = 0.5f
        } else {
            // NO selected
            yesButton.alpha = 0.5f
            noButton.alpha = 1.0f
        }
        
        // Enable next button
        nextButton.isEnabled = true
    }
    
    private fun resetButtonStates() {
        yesButton.alpha = 1.0f
        noButton.alpha = 1.0f
    }
    
    private fun goToNextQuestion() {
        if (currentAnswer == null) {
            return
        }
        
        if (currentQuestionIndex < questions.size - 1) {
            // Go to next question
            showQuestion(currentQuestionIndex + 1)
        } else {
            // All questions answered, return to MainActivity with results
            finishQuestionnaire()
        }
    }
    
    private fun finishQuestionnaire() {
        val resultIntent = Intent()
        resultIntent.putExtra("answers", answers)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Allow back button to cancel
        super.onBackPressed()
    }
}

