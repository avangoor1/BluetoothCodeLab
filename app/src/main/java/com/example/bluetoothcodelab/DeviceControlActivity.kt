package com.example.bluetoothcodelab

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.w3c.dom.Text
import java.util.UUID

private const val TAG = "BluetoothLeService"

class DeviceControlActivity : AppCompatActivity() {

    private var bluetoothService : BluetoothLeService? = null
    private var connected : Boolean? = null
    private var deviceAddress: String? = "5A:1B:2A:66:F6:7D" // Initialize this as null


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

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("EXTRA_DATA")?.let { data ->
                runOnUiThread {
                    // Update the UI with the received data
                    findViewById<TextView>(R.id.information).text = data
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.bluetooth.ACTION_DATA_RECEIVED")
        registerReceiver(dataReceiver, filter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.gatt_services_characteristics)

        // Now retrieve the device address from the Intent
        deviceAddress = intent?.getStringExtra("DeviceAddress")
        Log.d(TAG, "Device address received: $deviceAddress")

        // Initialize the TextView after setContentView
        val deviceAddressTextView = findViewById<TextView>(R.id.DeviceAddress)
        deviceAddressTextView.text = deviceAddress ?: "Unknown Device"

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("received broadcast message", "success")
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    updateConnectionState(R.string.connected)
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    updateConnectionState(R.string.disconnected)
                }
            }
        }
    }



    private fun updateConnectionState(resourceId: Int) {
        val status = findViewById<TextView>(R.id.status)
        // Assuming `statusTextView` is a TextView displaying connection status to the user
        status.text = getString(resourceId)
    }

    override fun onResume() {
        super.onResume()
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
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(dataReceiver)
    }



}