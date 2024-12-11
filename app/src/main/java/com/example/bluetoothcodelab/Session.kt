package com.example.bluetoothcodelab

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class Session : AppCompatActivity(){

    private lateinit var prevSessionBtn : Button
    private lateinit var newSessionBtn : Button
    private lateinit var newSessionTV : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.session)


        prevSessionBtn = findViewById(R.id.prevSessionBtn)
        newSessionBtn = findViewById(R.id.newSessionBtn)
        newSessionTV = findViewById(R.id.sessionET)

        newSessionBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("Session", newSessionTV.text.toString())
            Log.d("New Session in session", newSessionTV.text.toString())
            startActivity(intent)
        }

        // prev session btn click
        prevSessionBtn.setOnClickListener {
            val intent = Intent(this, PreviousSessions::class.java)
            startActivity(intent)
        }

    }

}