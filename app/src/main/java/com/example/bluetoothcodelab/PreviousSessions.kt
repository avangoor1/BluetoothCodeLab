package com.example.bluetoothcodelab

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.bluetoothcodelab.data.ShockingDao
import com.example.bluetoothcodelab.data.ShockingData
import com.example.bluetoothcodelab.data.ShockingRoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PreviousSessions : AppCompatActivity() {

    private lateinit var list : ListView
    private lateinit var roomDatabase: ShockingRoomDatabase
    private lateinit var shockingDao: ShockingDao
    private lateinit var sessions : TextView
    private lateinit var backButton : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.previous_sessions)

        list = findViewById(R.id.sessionListView)
        sessions = findViewById(R.id.tvsessions)
        backButton = findViewById(R.id.backButton)

        roomDatabase = ShockingRoomDatabase.getDatabase(applicationContext, CoroutineScope(
            Dispatchers.Main)
        )
        shockingDao = roomDatabase.shockingDao()


        getUniqueSessions { uniqueSessions ->
            // Handle the result on the Main thread
            Log.d("Unique Sessions", uniqueSessions.joinToString(", "))
            sessions.text = uniqueSessions.toString()

            // Set the data to ListView
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,  // A simple layout for each list item
                uniqueSessions
            )
            list.adapter = adapter

            // Set the OnItemClickListener for the ListView
            list.setOnItemClickListener { parent, view, position, id ->
                val selectedSession = uniqueSessions[position]
                // Handle the click event here
                Log.d("Session clicked", selectedSession)
                // You can perform any action here, e.g., navigate to another activity or fragment
                CoroutineScope(Dispatchers.Main).launch {
                    val deferred =
                        getSessionFunc(selectedSession.toString()) // Returns a Deferred object
                    val sessionData = deferred.await() // Await the result

                    // Log the actual data
                    Log.d("Result Data After", sessionData.toString())

                    if (sessionData != null) {
                        sendEmailWithCSVAttachment(
                            sessionData,
                            this@PreviousSessions,
                            selectedSession
                        )
                    }

                }

            }

        }

        backButton.setOnClickListener {
            val intent = Intent(this, Session::class.java)
            startActivity(intent)
        }

    }

    private fun getSessionFunc(session: String): Deferred<List<ShockingData>?> {
        return CoroutineScope(Dispatchers.IO).async {
            Log.d("Session Name: ", session)
            return@async shockingDao.getSession(session)
        }
    }

    fun convertSessionDataToCSV(sessionData: List<ShockingData>): String {
        val csvBuilder = StringBuilder()

        // Header row - define the columns (you can customize this based on your ShockingData fields)
        csvBuilder.append("ID,Timestamp,Session Name,Packet,Wave Form Type,Voltage,Amps,Watts,Frequency1,Frequency2,Duty1,Duty2,Latitude,Longitude,Request Packet,Done,Tick Rate\n")

        // Data rows
        for (data in sessionData) {
            csvBuilder.append("${data.id},${data.timestamp},${data.session},${data.Packet},${data.WaveFormType},${data.Voltage}, ${data.Amps}, ${data.Watts}, ${data.Frequency1}, ${data.Frequency2}, ${data.Duty1}, ${data.Duty2}, ${data.latitude}, ${data.longitude}, ${data.requestPacket}, ${data.done}, ${data.tickRate}\n")
        }

        return csvBuilder.toString()
    }

    fun saveCSVToFile(csvData: String, context: Context, sessionName : String): File? {
        // Create a file in the app's external storage directory or internal storage
        val file = File(context.getExternalFilesDir(null), "${sessionName}.csv")

        try {
            val fileOutputStream = FileOutputStream(file)
            fileOutputStream.write(csvData.toByteArray())
            fileOutputStream.close()
            return file
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }


    fun sendEmailWithCSVAttachment(sessionData: List<ShockingData>, context: Context, sessionName : String) {
        // Convert session data to CSV format
        val csvData = convertSessionDataToCSV(sessionData)

        // Save CSV data to a file
        val csvFile = saveCSVToFile(csvData, context, sessionName)

        if (csvFile != null) {
            // Create the email intent
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "vnd.android.cursor.dir/email"  // MIME type for sending email
                putExtra(Intent.EXTRA_EMAIL, arrayOf("ananya.vangoor@gmail.com"))  // Recipient email address
                putExtra(Intent.EXTRA_SUBJECT, "Session Data in CSV Format")  // Email subject
                putExtra(Intent.EXTRA_TEXT, "Attached is the session data in CSV format.")  // Email body

                // Attach the CSV file
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", csvFile)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  // Grant permission to read the file
                putExtra(Intent.EXTRA_STREAM, uri)
            }

            try {
                // Start the email client
                context.startActivity(Intent.createChooser(emailIntent, "Send email..."))
            } catch (e: ActivityNotFoundException) {
                // Handle case where no email client is found
                Toast.makeText(context, "No email client installed.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Error saving CSV file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getUniqueSessions(onResult: (List<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val uniqueSessions = shockingDao.getUniqueSessionNames()
            // Pass the result to the callback function
            withContext(Dispatchers.Main) {
                onResult(uniqueSessions)
            }
        }
    }

}