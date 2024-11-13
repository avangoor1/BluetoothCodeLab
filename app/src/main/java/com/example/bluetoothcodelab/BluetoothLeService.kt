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
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter.EXTRA_DATA
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.UUID

private const val TAG = "BluetoothLeService"

class BluetoothLeService : Service() {



    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED

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
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
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

                                writeDataToDevice(gatt, characteristic, "43")
                                Log.d("Send Data Over", "Success")

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

            // Interpreting the Data
            parseHexToDecimal(stringValue)

        }

        fun parseHexToDecimal(hexData: String): Int {
            // Convert each hex string to a decimal integer
            Log.d("Int data", hexData.toInt(16).toString())
            return hexData.toInt(16) // Convert from hex to decimal

        }

//        fun parseHexStringToBinary(hexString: String): String {
//            // Process each hex pair (byte) and convert to binary
//            return hexString.chunked(2) // Each chunk represents one byte
//                .joinToString(separator = "") { hex ->
//                    hex.toInt(16).toString(2).padStart(8, '0') // Convert to binary, pad to 8 bits
//                }
//        }


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
            Log.d("Bluetooth Write", "Write successful")
        } else {
            Log.w("Bluetooth Write", "Write failed")
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