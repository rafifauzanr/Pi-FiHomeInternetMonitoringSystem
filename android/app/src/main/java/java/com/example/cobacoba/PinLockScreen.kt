package com.example.cobacoba

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PinLockScreen : AppCompatActivity() {

    private lateinit var pinEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_lock_screen) // You'll need to create this layout

        pinEditText = findViewById(R.id.pinEditText)
        val enterButton: Button = findViewById(R.id.enterButton)

        enterButton.setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            val storedPin = getStoredPin()

            if (enteredPin == storedPin) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "PIN Salah!", Toast.LENGTH_SHORT).show()
                pinEditText.text.clear()
            }
        }
    }

    private fun getStoredPin(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("pin", "1234") ?: "1234" // Default PIN is 1234
    }
}

