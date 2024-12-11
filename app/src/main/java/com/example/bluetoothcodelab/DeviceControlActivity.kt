package com.example.bluetoothcodelab

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bluetoothcodelab.data.ShockingDao
import com.example.bluetoothcodelab.data.ShockingRoomDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.Manifest
import android.content.ActivityNotFoundException
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.example.bluetoothcodelab.data.ShockingData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

import org.w3c.dom.Text
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "BluetoothLeService"

class DeviceControlActivity : AppCompatActivity() {

    private var bluetoothService : BluetoothLeService? = null
    private var connected : Boolean? = null
    private var deviceAddress: String? = "5A:1B:2A:66:F6:7D" // Initialize this as null
    private var sessionName : String? = ""

    private lateinit var voltageOutput : TextView
    private lateinit var frequencyOutput : TextView
    private lateinit var dutyCycleOutput : TextView
    private lateinit var waveformOutput : TextView
    private lateinit var latitudeOutput : TextView
    private lateinit var longitudeOutput : TextView
    private lateinit var ampsOutput : TextView
    private lateinit var wattsOutput : TextView
    private lateinit var requestPacketButton : Button
    private lateinit var doneButton : Button
    private lateinit var timeSpinner : Spinner

    private lateinit var roomDatabase: ShockingRoomDatabase
    private lateinit var shockingDao: ShockingDao

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var latitude : String = "0.0"
    private var longitude : String = "0.0"




    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                // call functions on service to check connection and connect to devices
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
                    finish()
                }
                // perform device connection
                Log.d("Service Connection", "called")
                deviceAddress?.let { bluetooth.connect(it) }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    companion object {
        const val PERMISSION_REQUEST_ACCESS_LOCATION = 100 // Unique code for permission request
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_ACCESS_LOCATION)
    }


    private fun getLastLocation(onLocationReceived: (Pair<Double, Double>) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude.toString()
                    longitude = location.longitude.toString()
                } else {
                    Toast.makeText(this, "Location not found", Toast.LENGTH_LONG).show()
                    onLocationReceived(Pair(0.0, 0.0)) // Return default values if location is null
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_LONG).show()
                onLocationReceived(Pair(0.0, 0.0)) // Return default values if fetching fails
            }
        } else {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_LONG).show()
            onLocationReceived(Pair(0.0, 0.0)) // Return default values if permission is missing
        }
    }

    suspend fun getLastLocation2(
        fusedLocationClient: FusedLocationProviderClient,
        context: Context
    ): Pair<Double, Double> {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(Pair(location.latitude, location.longitude))
                    } else {
                        Toast.makeText(context, "Location not found", Toast.LENGTH_LONG).show()
                        continuation.resume(Pair(0.0, 0.0)) // Return default values if location is null
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to get location", Toast.LENGTH_LONG).show()
                    continuation.resume(Pair(0.0, 0.0)) // Return default values if fetching fails
                }
            }
        } else {
            Toast.makeText(context, "Permission not granted", Toast.LENGTH_LONG).show()
            return Pair(0.0, 0.0) // Return default values if permission is missing
        }
    }

    fun updateLocation(fusedLocationClient: FusedLocationProviderClient, context: Context) {
        GlobalScope.launch(Dispatchers.Main) {
            // Get the location values
            val (lat, lon) = getLastLocation2(fusedLocationClient, context)

            // Update the lateinit variables
            latitude = lat.toString()
            longitude = lon.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.gatt_services_characteristics)

        // Now retrieve the device address from the Intent
        deviceAddress = intent?.getStringExtra("DeviceAddress")
        sessionName = intent?.getStringExtra("SessionName").toString()
        Log.d("New Session in Device Activity", sessionName.toString())
        Log.d(TAG, "Device address received: $deviceAddress")

        // Initialize the TextView after setContentView
        val deviceAddressTextView = findViewById<TextView>(R.id.DeviceAddress)
        deviceAddressTextView.text = deviceAddress ?: "Unknown Device"

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check and request permissions
        if (checkPermissions()) {
            getLastLocation { coordinates ->
                val (latitude, longitude) = coordinates
                Log.d("Location", "Latitude: $latitude, Longitude: $longitude")
            }
        } else {
            requestPermissions()
        }

        requestPacketButton = findViewById(R.id.requestPacket)
        doneButton = findViewById(R.id.doneButton)
        timeSpinner = findViewById(R.id.time)


        voltageOutput = findViewById(R.id.voltage)
        frequencyOutput = findViewById(R.id.frequency)
        dutyCycleOutput = findViewById(R.id.dutyCycle)
        waveformOutput = findViewById(R.id.waveform)
        latitudeOutput = findViewById(R.id.latitude)
        longitudeOutput = findViewById(R.id.longitude)
        ampsOutput = findViewById(R.id.AmpsOutput)
        wattsOutput = findViewById(R.id.wattsOutput)

        roomDatabase = ShockingRoomDatabase.getDatabase(applicationContext, CoroutineScope(
            Dispatchers.Main)
        )
        shockingDao = roomDatabase.shockingDao()

        // Create an array of options for the Spinner
        val options = arrayOf("0", "5", "15", "30")

        // Create an ArrayAdapter using the options array
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)

        // Set the layout for the dropdown menu
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Set the adapter to the Spinner
        timeSpinner.adapter = adapter

        timeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                // Get the selected item
                val selectedItem = parent.getItemAtPosition(position) as String

                // Do something with the selected item
                Toast.makeText(this@DeviceControlActivity, "Selected: $selectedItem", Toast.LENGTH_SHORT).show()

                // val shockingData = ShockingData(null, "", "", "", "", "", "", "", "", "", "", "", "0", "0", false, false, selectedItem)
                // add this data to the database
//                Log.d("Inserting data", shockingData.toString())
//                insertData(shockingData)

            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do something when nothing is selected
                Toast.makeText(this@DeviceControlActivity, "Nothing selected", Toast.LENGTH_SHORT).show()
            }
        }

        requestPacketButton.setOnClickListener {
            val shockingData = ShockingData(null, "", "", "", "", "", "", "", "", "", "", "", "0", "0", true, false, "-")
            // add this data to the database
            Log.d("Inserting data", shockingData.toString())
            insertData(shockingData)
        }

        doneButton.setOnClickListener {

            CoroutineScope(Dispatchers.Main).launch {
                val deferred = getSessionFunc(sessionName.toString()) // Returns a Deferred object
                val sessionData = deferred.await() // Await the result

                // Log the actual data
                Log.d("Result Data After", sessionData.toString())

                if (sessionData != null) {
                    sendEmailWithCSVAttachment(sessionData, this@DeviceControlActivity)
                }

            }

            val intent = Intent(this, Session::class.java)
            startActivity(intent)

        }



        fun parseHexToDecimal(hexData: String): Int {
            // Convert each hex string to a decimal integer
            Log.d("Int data", hexData.toInt(16).toString())
            return hexData.toInt(16) // Convert from hex to decimal

        }

        fun parseHexToBinary(hexData: String): String {
            val decimalValue = hexData.toInt(16)

            // Convert the integer to a binary string
            val binaryString = decimalValue.toString(2)

            // Return the binary string
            return binaryString
        }

        fun waveformType(bindata1 : String) : String {
            Log.d("wave form type", bindata1)
            var bindata = bindata1
            while (bindata.length < 8) {
                bindata = "0" + bindata1
            }
            Log.d("Bindata", bindata)
            var channel = bindata.substring(0,1)
            var activeWaveForm = bindata.substring(1, 2)
            var secondChannelEnable = bindata.substring(2, 3)
            var waveFormType = bindata.substring(3, 8)
            if (waveFormType.substring(4,5) == "1") {
                return "DC"
            }
            else if (waveFormType.substring(3,4) == "1") {
                return "BR"
            }
            else if (waveFormType.substring(2,3) == "1") {
                return "AD"
            }
            else if (waveFormType.substring(1,2) == "1") {
                return "NR"
            }
            else if (waveFormType.substring(0,1) == "1") {
                return "AC"
            }
            return "NA"
        }

        shockingDao.getLatestEntry().observe(this) { latestEntry ->
            latestEntry?.let {

                // Update the TextView with the data from the latest entry
                val packet = it

                var shockingData = ShockingData(packet.id, packet.timestamp, packet.session, packet.Packet, "5", "5", "-", "-", "", "", "", "", "0", "0", false, false, "15")

                if (packet.Packet.length > 10 && packet.Packet.substring(4, 6) == "04") {
                    Log.d("Going in here inserted packet", "success")

                    var waveform = packet.Packet.substring(6, 8)
                    waveform = parseHexToBinary(waveform)
                    waveform = waveformType(waveform)

                    Log.d("waveform", waveform)

                    var voltage = packet.Packet.substring(8, 10)
                    voltage = parseHexToDecimal(voltage).toString()

                    Log.d("Voltage", voltage)

                    var extraVoltage = packet.Packet.substring(10, 12)
                    extraVoltage = parseHexToDecimal(extraVoltage).toString()

                    Log.d("extra voltage", extraVoltage)

                    var frequency = packet.Packet.substring(12, 14)
                    frequency = parseHexToDecimal(frequency).toString()

                    Log.d("frequency", frequency)

                    var extraFrequency = packet.Packet.substring(14, 16)
                    extraFrequency = parseHexToDecimal(extraFrequency).toString()

                    Log.d("extra frequency", extraFrequency)

                    var dutyCycle = packet.Packet.substring(16, 18)
                    dutyCycle = parseHexToDecimal(dutyCycle).toString()
                    var frequency2 = packet.Packet.substring(18, 20)
                    frequency2 = parseHexToDecimal(frequency2).toString()
                    var extraFrequency2 = packet.Packet.substring(20, 22)
                    extraFrequency2 = parseHexToDecimal(extraFrequency2).toString()
                    var duty2 = packet.Packet.substring(22, 24)
                    duty2 = parseHexToDecimal(duty2).toString()
                    var sampleRate = packet.Packet.substring(24, 26)
                    sampleRate = parseHexToDecimal(sampleRate).toString()
                    var checksum = packet.Packet.substring(26, 28)
                    checksum = parseHexToDecimal(checksum).toString()
                    var offset = packet.Packet.substring(28, 30)
                    offset = parseHexToDecimal(offset).toString()

                    updateLocation(fusedLocationClient, this)

                    shockingData = ShockingData(packet.id, packet.timestamp, sessionName.toString(), packet.Packet, waveform, voltage, "-", "-", frequency, frequency2, dutyCycle, duty2, latitude, longitude, false, false, "-")

                    updateData(shockingData)

                    waveformOutput.text = waveform
                    voltageOutput.text = voltage
                    frequencyOutput.text = frequency
                    dutyCycleOutput.text = dutyCycle
                    latitudeOutput.text = latitude
                    longitudeOutput.text = longitude
                    ampsOutput.text = "-"
                    wattsOutput.text = "-"

                }

                if (packet.Packet.length > 10 && packet.Packet.substring(4, 6) == "05") {
                    Log.d("Going in here inserted packet", "success")
                    Log.d("Packet", packet.toString())

                    var waveform = packet.Packet.substring(6, 8)
                    waveform = parseHexToBinary(waveform)
                    waveform = waveformType(waveform)

                    var voltage = packet.Packet.substring(8, 10)
                    voltage = parseHexToDecimal(voltage).toString()


                    var extraVoltage = packet.Packet.substring(10, 12)
                    extraVoltage = parseHexToDecimal(extraVoltage).toString()

                    var amps = packet.Packet.substring(12, 14)
                    amps = parseHexToDecimal(amps).toString()

                    var extraAmps = packet.Packet.substring(14, 16)
                    extraAmps = parseHexToDecimal(extraAmps).toString()

                    var watts = packet.Packet.substring(16, 18)
                    watts = parseHexToDecimal(watts).toString()

                    var extraWatts = packet.Packet.substring(18, 20)
                    extraWatts = parseHexToDecimal(extraWatts).toString()

                    var extraWatts2 = packet.Packet.substring(20, 22)
                    extraWatts2 = parseHexToDecimal(extraWatts2).toString()

                    var footswitch = packet.Packet.substring(22, 24)
                    footswitch = parseHexToDecimal(footswitch).toString()

                    var extrafootswitch = packet.Packet.substring(24, 26)
                    extrafootswitch = parseHexToDecimal(extrafootswitch).toString()

                    var extrafootswitch2 = packet.Packet.substring(26, 28)
                    extrafootswitch2 = parseHexToDecimal(extrafootswitch2).toString()

                    var voltageLSB = packet.Packet.substring(28, 30)
                    voltageLSB = parseHexToDecimal(voltageLSB).toString()

                    var battery = packet.Packet.substring(30, 32)
                    battery = parseHexToDecimal(battery).toString()

                    var powerSrc = packet.Packet.substring(32, 34)
                    powerSrc = parseHexToDecimal(powerSrc).toString()

                    var extrapowerSrc = packet.Packet.substring(34, 36)
                    extrapowerSrc = parseHexToDecimal(extrapowerSrc).toString()

                    var flags = packet.Packet.substring(36, 38)
                    flags = parseHexToDecimal(flags).toString()

                    var offset = packet.Packet.substring(38, 40)
                    offset = parseHexToDecimal(offset).toString()

                    var extraOffset = packet.Packet.substring(40, 42)
                    extraOffset = parseHexToDecimal(extraOffset).toString()

                    updateLocation(fusedLocationClient, this)

                    shockingData = ShockingData(packet.id, packet.timestamp, sessionName.toString(), packet.Packet, waveform, voltage, amps, watts, "-", "-", "-", "-", latitude, longitude, false, false, "-")

                    updateData(shockingData)

                    waveformOutput.text = waveform
                    voltageOutput.text = voltage
                    frequencyOutput.text = "-"
                    dutyCycleOutput.text = "-"
                    latitudeOutput.text = latitude
                    longitudeOutput.text = longitude
                    ampsOutput.text = amps
                    wattsOutput.text = watts

                }

//                if (packet.Packet.length > 10 && packet.Packet.substring(4, 6) == "09") {
//                    Log.d("Going into 09 packet type", "success")
//                    Log.d("Packet", packet.toString())
//
//                    var waveform = packet.Packet.substring(6, 8)
//                    waveform = parseHexToBinary(waveform)
//                    waveform = waveformType(waveform)
//
//                    var binaryDisplaySoftwareRev1 = packet.Packet.substring(8, 10)
//                    binaryDisplaySoftwareRev1 = parseHexToDecimal(binaryDisplaySoftwareRev1).toString()
//
//
//                    var binaryDisplaySoftwareRev2 = packet.Packet.substring(10, 12)
//                    binaryDisplaySoftwareRev2 = parseHexToDecimal(binaryDisplaySoftwareRev2).toString()
//
//                    var binaryPower1 = packet.Packet.substring(12, 14)
//                    binaryPower1 = parseHexToDecimal(binaryPower1).toString()
//
//                    var binaryPower2 = packet.Packet.substring(14, 16)
//                    binaryPower2 = parseHexToDecimal(binaryPower2).toString()
//
//                    var time = packet.Packet.substring(16, 24)
//                    time = parseHexToDecimal(time).toString()
//
//                    var serialNumber1 = packet.Packet.substring(24, 26)
//                    serialNumber1 = parseHexToDecimal(serialNumber1).toString()
//
//                    var serialNumber2 = packet.Packet.substring(26, 28)
//                    serialNumber2 = parseHexToDecimal(serialNumber2).toString()
//
//                    var checksum = packet.Packet.substring(28, 32)
//                    checksum = parseHexToDecimal(checksum).toString()
//
////                    updateLocation(fusedLocationClient, this)
//
////                    shockingData = ShockingData(packet.id, packet.timestamp, sessionName.toString(), packet.Packet, waveform, "Software rev: ${binaryDisplaySoftwareRev1}" , "Time: ${time}", "Serial Number: ${serialNumber1}", "-", "-", "-", "-", latitude, longitude, false, false, "-")
////
////                    updateData(shockingData)
//
////                    waveformOutput.text = waveform
////                    voltageOutput.text = "Software rev: ${binaryDisplaySoftwareRev1}"
////                    frequencyOutput.text = "Time: ${time}"
////                    dutyCycleOutput.text = "Serial Number: ${serialNumber1}"
////                    latitudeOutput.text = latitude
////                    longitudeOutput.text = longitude
////                    ampsOutput.text = "-"
////                    wattsOutput.text = "-"
//
//                }

            }
        }


    }

    //method to insert user into the database upon signup
    private fun insertData(data: ShockingData) {
        // Using a coroutine to insert the user into the database
        CoroutineScope(Dispatchers.IO).launch {
            shockingDao.insert(data)
            Log.d("Database", "Data inserted: $data")
        }
    }

    private fun getSessionFunc(session: String): Deferred<List<ShockingData>?> {
        return CoroutineScope(Dispatchers.IO).async {
            Log.d("Session Name: ", session)
            return@async shockingDao.getSession(session)
        }
    }

    //method to insert user into the database upon signup
    private fun updateData(data: ShockingData) {
        // Using a coroutine to insert the user into the database
        CoroutineScope(Dispatchers.IO).launch {
            shockingDao.update(data)
            Log.d("Database", "Data updated: $data")
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

    fun saveCSVToFile(csvData: String, context: Context): File? {
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


    fun sendEmailWithCSVAttachment(sessionData: List<ShockingData>, context: Context) {
        // Convert session data to CSV format
        val csvData = convertSessionDataToCSV(sessionData)

        // Save CSV data to a file
        val csvFile = saveCSVToFile(csvData, context)

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

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("received broadcast message", "success")
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    //updateConnectionState(R.string.connected)
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    //updateConnectionState(R.string.disconnected)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()

        // register receiver for data
        val filter2 = IntentFilter("com.example.bluetoothcodelab.CHARACTERISTIC_CHANGED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            //registerReceiver(characteristicChangeReceiver, filter2, Context.RECEIVER_NOT_EXPORTED)
            Log.d("Receiver registered1", "success")
        } else {
            //registerReceiver(characteristicChangeReceiver, filter2)
            Log.d("Receiver registered2", "success")
        }


        // Register the receiver for GATT updates
        val filter = makeGattUpdateIntentFilter()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gattUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "Registered gattUpdateReceiver with RECEIVER_NOT_EXPORTED")
        } else {
            registerReceiver(gattUpdateReceiver, filter)
            Log.d(TAG, "Registered gattUpdateReceiver")
        }

        // Connect to the Bluetooth device if the service is already bound
        if (bluetoothService == null){
            Log.d("its null", "null")
        }

        if (bluetoothService != null) {
            val result = bluetoothService!!.connect("5A:1B:2A:66:F6:7D")
            Log.d("DeviceControlsActivity Connection", "Connect request result=$result")
        }

    }


    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
//        unregisterReceiver(characteristicChangeReceiver)
        Log.d("Unregistered Receiver", "CharacterisDtic change receiver")
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }



}