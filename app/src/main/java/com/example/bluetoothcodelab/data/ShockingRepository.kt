package com.example.bluetoothcodelab.data

import androidx.annotation.WorkerThread

class ShockingRepository(private val shockingDao: ShockingDao){
    @WorkerThread
    suspend fun insert(data: ShockingData) {
        shockingDao.insert(data)
    }

    @WorkerThread
    suspend fun update(data: ShockingData){
        shockingDao.update(data)
    }

    @WorkerThread
    suspend fun delete(){
        shockingDao.deleteAll()
    }
}