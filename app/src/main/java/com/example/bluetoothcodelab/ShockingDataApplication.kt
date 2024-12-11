package com.example.bluetoothcodelab

import android.app.Application
import com.example.bluetoothcodelab.data.ShockingRepository
import com.example.bluetoothcodelab.data.ShockingRoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class ShockingDataApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())
    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { ShockingRoomDatabase.getDatabase(this,applicationScope) }
    val repository by lazy { ShockingRepository(database.shockingDao()) }

}