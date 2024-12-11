package com.example.bluetoothcodelab.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShockingDao {
    @Update
    suspend fun update(data: ShockingData)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(data: ShockingData)

    @Query("DELETE FROM shocking_data_table")
    suspend fun deleteAll()

    @Query("SELECT * FROM shocking_data_table WHERE Session = :session")
    suspend fun getSession(session : String) : List<ShockingData>

    @Query("SELECT DISTINCT Session FROM shocking_data_table")
    suspend fun getUniqueSessionNames(): List<String>

    @Query("SELECT * FROM shocking_data_table ORDER BY id DESC LIMIT 1") // Replace `id` with your primary key column
    fun getLatestEntry(): LiveData<ShockingData> // Replace `ShockingData` with your entity class name

}