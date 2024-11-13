package com.example.bluetoothcodelab

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.contains

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    private lateinit var deviceListView: ListView
    private lateinit var deviceArrayAdapter: ArrayAdapter<String>
    private val devices: MutableList<String> = mutableListOf()

    private var deviceName : String = ""
    private var deviceAddress : String = ""


    private val handler = Handler()
    private var scanning = false

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val SCAN_PERIOD: Long = 20000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupWindowInsets()

        initializeBluetooth()
        setupListView()
        registerBluetoothEnableLauncher()

        if (bluetoothAdapter?.isEnabled == false) {
            requestBluetoothPermission()
        } else {
            startScanning()
            Log.d("Bluetooth", "Started scanning for devices")
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeBluetooth() {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
        } else {
            Log.d("Device Bluetooth", "Supported")
        }
    }

    private fun setupListView() {
        deviceListView = findViewById(R.id.list_view)
        deviceArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices)
        deviceListView.adapter = deviceArrayAdapter
    }

    private fun registerBluetoothEnableLauncher() {
        enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth is enabled", Toast.LENGTH_SHORT).show()
                startScanning()
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestBluetoothPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_ENABLE_BT
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ENABLE_BT && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermission()
        } else {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScanning() {
        if (!scanning) {
            // Ensure all permissions are granted
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermission()
                return
            }

            scanning = true
            Log.d("Bluetooth", "Started scanning for devices")
            handler.postDelayed({ stopScanning() }, SCAN_PERIOD)

            bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
        }
    }

    private fun stopScanning() {
        scanning = false
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
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
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        Log.d("Stop Scan", "called")

        val deviceInfo = "${deviceName} - ${deviceAddress}"
        Log.d("INfo", deviceInfo)
        if (devices.contains(deviceInfo)){
            Log.d("Bruh TV", "sending over intent")
            val intent = Intent(this@MainActivity, DeviceControlActivity::class.java)
            intent.putExtra("DeviceAddress", deviceAddress)
            Log.d("Bruh Intent", deviceAddress)
            startActivity(intent)
        }

    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("Going in here", "scan result")
            super.onScanResult(callbackType, result)

            // Check if Bluetooth permission has been granted
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Handle the case where permission is not granted
                return
            }

            if (result.device.name != null) {
                // Create device info string
                val deviceInfo = "${result.device.name ?: "Unknown Device"} - ${result.device.address}"

                // Print the device info to the log
                Log.d("BluetoothDevice", "Device found: $deviceInfo")

                if (result.device.name == ("BackPack Unit1")){
                    deviceName = result.device.name.toString()
                    deviceAddress = result.device.address.toString()
                }

                // If you want to keep adding the devices to the list for later, you can keep this
                if (!devices.contains(deviceInfo)) {
                    devices.add(deviceInfo)
                    deviceArrayAdapter.notifyDataSetChanged() // Update the ListView if needed
                }
            }
        }
    }
}
