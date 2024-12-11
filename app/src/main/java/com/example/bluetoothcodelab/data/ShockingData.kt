package com.example.bluetoothcodelab.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.sql.Time


@Entity(tableName = "shocking_data_table")
data class ShockingData (
    @PrimaryKey(autoGenerate = true) val id: Int?,
    @ColumnInfo(name = "Timestamp") val timestamp : String,
    @ColumnInfo(name = "Session") val session : String,
    @ColumnInfo(name = "Packet") val Packet : String,
    @ColumnInfo(name = "Wave Form Type") val WaveFormType : String,
    @ColumnInfo(name = "Voltage") val Voltage : String,
    @ColumnInfo(name = "Amps") val Amps : String,
    @ColumnInfo(name = "Watts") val Watts : String,
    @ColumnInfo(name = "Frequency1") val Frequency1 : String,
    @ColumnInfo(name = "Frequency2") val Frequency2 : String,
    @ColumnInfo(name = "Duty1") val Duty1 : String,
    @ColumnInfo(name = "Duty2") val Duty2 : String,
    @ColumnInfo(name = "Latitude") val latitude : String,
    @ColumnInfo(name = "Longitude") val longitude : String,
    @ColumnInfo(name = "RequestPacket") val requestPacket : Boolean,
    @ColumnInfo(name = "Done") val done : Boolean,
    @ColumnInfo(name = "Tick Rate") val tickRate : String
)