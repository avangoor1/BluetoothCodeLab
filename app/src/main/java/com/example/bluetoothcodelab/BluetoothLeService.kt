package com.example.bluetoothcodelab

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.STATE_CONNECTED
import android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter.EXTRA_DATA
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.bluetoothcodelab.data.ShockingDao
import com.example.bluetoothcodelab.data.ShockingData
import com.example.bluetoothcodelab.data.ShockingRoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "BluetoothLeService"

class BluetoothLeService : Service() {

    private lateinit var roomDatabase: ShockingRoomDatabase
    private lateinit var shockingDao: ShockingDao


    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED

    private var insert: Boolean = false  // Initialized with 'false'

    private val stringArray = mutableListOf<String>()  // To store the received packets
    private var isInPacket = false  // Flag to check if we are in the middle of a packet
    private var currentPacket = StringBuilder()  // To accumulate bytes for the current packet
    private val payload = StringBuilder()
    private var headerCheck = StringBuilder()  // Temporary buffer to check for the header
    val completePackets = mutableListOf<String>()
    val packetMutableSet = mutableSetOf("")
    var consecutive38Count = 0 // Counter for consecutive "38"
    val endSequence = "38" // The value we're checking for
    val header = "55AA" // Valid packet header

    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private var writeTarget : BluetoothGattCharacteristic? = null


    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

    }



    fun initialize(): Boolean {
        roomDatabase = ShockingRoomDatabase.getDatabase(applicationContext, CoroutineScope(
            Dispatchers.Main))
        shockingDao = roomDatabase.shockingDao()

        //deleteAllData()

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }

        val filter = IntentFilter("com.example.bluetoothcodelab.REQUEST_PACKET")
        registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)


        shockingDao.getLatestEntry().observeForever { latestEntry ->
            latestEntry?.let {
                // Handle the latest entry here
                val row = it

                if (row.requestPacket == true) {
                    // write a 1 to the bluetooth
                    bluetoothGatt?.let { it1 -> writeTarget?.let { it2 ->
                        writeDataToDevice(it1,
                            it2, "3")
                    } }

                    val shockingData = ShockingData(null, "", "", "", "", "", "", "", "", "", "", "", "0", "0", false, false, "-")
                    // add this data to the database
                    Log.d("Inserting data", shockingData.toString())
                    var count = 0
                    if (count == 0) {
                        insertData(shockingData)
                        count += 1
                    }
                }
//
//                // Store the handler to control the periodic task
//                var handler: Handler? = null
//
//                // Start the periodic task if tickRate == "5"
//                if (row.tickRate == "5") {
//                    // If handler is null, start the periodic task
//                    if (handler == null) {
//                        handler = Handler(Looper.getMainLooper())
//                        handler?.postDelayed(object : Runnable {
//                            override fun run() {
//                                // Your logic to perform every 5 seconds
//                                if (row.tickRate == "5") {
//                                    // Execute your action every 5 seconds
//                                    bluetoothGatt?.let { it1 ->
//                                        writeTarget?.let { it2 ->
//                                            writeDataToDevice(it1, it2, "3")
//                                        }
//                                    }
//
//                                    // Schedule the next execution in 5 seconds
//                                    handler?.postDelayed(this, 5000)
//                                } else {
//                                    // If tickRate is no longer "5", cancel the task
//                                    handler?.removeCallbacks(this)
//                                    handler = null  // Clear the handler to prevent further use
//                                    Log.d("Task stopped", "Periodic task stopped as tickRate is no longer 5.")
//                                }
//                            }
//                        }, 0) // Initial delay is 0, task runs immediately
//                    }
//                }
//
//                // Stop the periodic task if tickRate == "0"
//                if (row.tickRate == "0") {
//                    // If tickRate is "0", stop the periodic task
//                    handler?.removeCallbacksAndMessages(null)
//                    handler = null  // Clear the handler to ensure no future task is posted
//                    Log.d("Task stopped", "Periodic task stopped as tickRate is 0.")
//                }

            }
        }

        return true
    }

    //method to insert user into the database upon signup
    private fun insertData(data: ShockingData) {
        // Using a coroutine to insert the user into the database
        CoroutineScope(Dispatchers.IO).launch {
            shockingDao.insert(data)
            Log.d("Database", "Data inserted: $data")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                // connect to the GATT server on the device
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                Log.d("ConnectionState", "Current connection state: $connectionState")
                return true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Device not found with provided address. Unable to connect.")
                return false
            }
        } ?: run {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return false
        }
    }

    private fun broadcastUpdate(action: String) {
        Log.d("Broadcast", "sending $action")
        val intent = Intent(action)
        sendBroadcast(intent)
        Log.d("Broadcast", "sent $action")
        Log.d("ConnectionState", "Current connection state: $connectionState")
    }

    private var currentConnectionAttempt = 1
    private var MAXIMUM_CONNECTION_ATTEMPTS = 5

    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // successfully connected to the GATT Server
                    connectionState = STATE_CONNECTED
                    bluetoothGatt?.discoverServices()
                    broadcastUpdate(ACTION_GATT_CONNECTED)
                    Log.d("Connection", "success")
                    bluetoothGatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // disconnected from the GATT Server
                    connectionState = STATE_DISCONNECTED
                    broadcastUpdate(ACTION_GATT_DISCONNECTED)
                    Log.d("Connection", "disconnected")
                }
            } else {
                gatt.close()
                currentConnectionAttempt+=1
                if(currentConnectionAttempt<=MAXIMUM_CONNECTION_ATTEMPTS){
                    connect("5A:1B:2A:66:F6:7D")
                    Log.d("Connected", "We are connected")
                }else{
                    Log.d("can't connect", "no connection")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
//            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED

                gatt?.services?.forEach { service ->
                    if (service.uuid.toString() == "0000180c-0000-1000-8000-00805f9b34fb") {

                        Log.d("Service UUID", service.uuid.toString())
                        // If you want to access characteristics for each service

                        service.characteristics.forEach { characteristic ->
                            Log.d("Characteristic UUID", characteristic.uuid.toString())
                            val characteristic_value = gatt.getService(service.uuid)
                                ?.getCharacteristic(characteristic.uuid)
                            targetCharacteristic = gatt.getService(service.uuid)?.getCharacteristic(characteristic.uuid)
                            if (characteristic.uuid.toString() == "00002a59-0000-1000-8000-00805f9b34fb") {
                                writeTarget = gatt.getService(service.uuid)?.getCharacteristic(characteristic.uuid)
//                                writeDataToDevice(gatt, characteristic, "6")
//                                Log.d("Send Data Over For 09 Packet", "Success")
                            }
                            if (characteristic_value != null) {
                                if (ActivityCompat.checkSelfPermission(
                                        this@BluetoothLeService,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    // TODO: Consider calling
                                    //    ActivityCompat#requestPermissions
                                    // here to request the missing permissions, and then overriding
                                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                    //                                          int[] grantResults)
                                    // to handle the case where the user grants the permission. See the documentation
                                    // for ActivityCompat#requestPermissions for more details.
                                    return
                                }
                                // Enable notifications for the characteristic
                                gatt.setCharacteristicNotification(characteristic_value, true)

                                // Get the descriptor and write to it to enable notifications
                                val descriptor = characteristic_value.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                )
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
                                    Log.d("Descriptor", "Notification enabled for ${characteristic_value.uuid}")
                                } else {
                                    Log.e("Descriptor", "Descriptor not found for ${characteristic_value.uuid}")
                                }
                                if (characteristic.uuid.toString() == "00002a58-0000-1000-8000-00805f9b34fb") {
                                    writeDataToDevice(gatt, characteristic, "43")
                                    Log.d("Send Data Over", "Success")
                                }

                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        // Buffer to store incoming data
        private val dataBuffer = mutableListOf<Byte>()

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("GATT connection", "Success")
                val value = characteristic.value
                if (value != null) {
                    // Append incoming data to the buffer
                    dataBuffer.addAll(value.toList())

                    Log.d("Data buffer", dataBuffer.toString())

                    // Check for the end of the message, e.g., a newline character (10)
                    if (dataBuffer.contains(10.toByte())) {
                        // Convert buffer to a string, filtering out nulls or padding
                        val stringValue = dataBuffer.toByteArray().toString(Charsets.UTF_8).trim()
                        Log.d("Characteristic Value (Notification)", stringValue)

                        // Clear the buffer after processing the complete message
                        dataBuffer.clear()
                    }
                }
//                Log.d("Characteristic Value", (value ?: "No value").toString())
//                // Convert byte array to a readable format if needed
//                val stringValue = String(value, Charsets.UTF_8)
//                Log.d("Characteristic String Value", stringValue)
            } else {
                Log.w("Characteristic Read", "Failed to read characteristic: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {

            // This method is called when a notification is received
            val value = characteristic.value
            Log.d("Characteristic Value (Notification)", value?.contentToString() ?: "No value")

            // Convert byte array to a readable format if needed
            val stringValue = String(value, Charsets.UTF_8)
            Log.d("Characteristic String Value (Notification)", stringValue)

            if (!isInPacket) {
                // Look for valid header (55AA)
                if (currentPacket.length >= 4 && currentPacket.substring(0, 4) == header) {
                    // Found valid header, start the packet
                    isInPacket = true
                    if (payload.isNotEmpty()) {
                        completePackets.add(payload.toString()) // Add the completed packet to the list
                        Log.d("Packet Completed: ", payload.toString())
                    }
                    payload.clear() // Clear any previous payload
                    payload.append(currentPacket) // Include the header in the payload
                    payload.append(stringValue) // Append the current byte to payload
                    currentPacket.clear() // Reset current packet builder to start the payload
                } else {
                    // Accumulate bytes until the header is found
                    currentPacket.append(stringValue)
                    Log.d("Current Packet: ", currentPacket.toString())
                }
            } else {
                // We're in a valid packet, continue collecting the payload
                Log.d("String Value: ", stringValue)
                payload.append(stringValue)
                Log.d("Payload", payload.toString())

                // Check for end condition (three consecutive 38s)
                if (stringValue == endSequence) {
                    Log.d("Going into end sequence", "good")
                    consecutive38Count++
                    if (consecutive38Count == 3) {
                        // Packet is complete
                        Log.d("Packet End Detected", "Packet ends after three consecutive 38s")
                        isInPacket = false
                        // Add the completed packet to the list
                        completePackets.add(payload.toString())
                        Log.d("Complete Packet: ", payload.toString())

                        // Process the completed packet (optional)
                        val timestamp = System.currentTimeMillis()
                        val shockingData = ShockingData(
                            null,
                            timestamp.toString(),
                            "",
                            payload.substring(0, payload.length - 6).toString(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            false,
                            false,
                            ""
                        )

                        insertData(shockingData)

                        // Clear payload and reset counters
                        payload.clear()
                        consecutive38Count = 0
                    }
                } else {
                    // Reset counter if sequence breaks
                    consecutive38Count = 0
                }
            }

            // Interpreting the Data
            parseHexToDecimal(stringValue)


        }


        //method to insert user into the database upon signup
        private fun insertData(data: ShockingData) {
            // Using a coroutine to insert the user into the database
            CoroutineScope(Dispatchers.IO).launch {
                shockingDao.insert(data)
                Log.d("Database", "Data inserted: $data")
            }
        }

        fun parseHexToDecimal(hexData: String): Int {
            // Convert each hex string to a decimal integer
            Log.d("Int data", hexData.toInt(16).toString())
            return hexData.toInt(16) // Convert from hex to decimal

        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("Characteristic Write", "Write successful")
            } else {
                Log.w("Characteristic Write", "Write failed with status: $status")
            }
        }

    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("coming in to the onReceive", "success")
            if (intent.action == "com.example.bluetoothcodelab.REQUEST_PACKET") {

                // Write to the characteristic
                targetCharacteristic?.let { characteristic ->
                    characteristic.value = byteArrayOf(0x31)
                    if (ActivityCompat.checkSelfPermission(
                            this@BluetoothLeService,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    bluetoothGatt?.writeCharacteristic(characteristic)
                    Log.d("BluetoothLeService", "Writing to characteristic: ${characteristic.uuid}")

                }
            }
        }
    }

    private fun deleteAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            shockingDao.deleteAll()
            Log.d("Database", "All deleted")
        }
    }

    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        return bluetoothGatt?.services
    }

    fun writeDataToDevice(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: String) {
        // Convert the string data to a byte array
        val byteArray = data.toByteArray(Charsets.UTF_8)

        // Set the value of the characteristic
        characteristic.value = byteArray

        // Check if permissions are granted
        if (ActivityCompat.checkSelfPermission(this@BluetoothLeService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Handle permission request if needed
            return
        }

        // Write the characteristic
        val writeSuccess = gatt.writeCharacteristic(characteristic)
        if (writeSuccess) {
            Log.d("Bluetooth Write", data)
        } else {
            Log.w("Bluetooth Write", "Write Failed")
        }
    }


    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    @SuppressLint("MissingPermission")
    private fun close() {
        bluetoothGatt?.let { gatt ->
            gatt.close()
            bluetoothGatt = null
        }
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }
}