package com.example.bluetoothcodelab.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = arrayOf(ShockingData::class), version = 7, exportSchema = false)
public abstract class ShockingRoomDatabase : RoomDatabase() {

    abstract fun shockingDao(): ShockingDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: ShockingRoomDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): ShockingRoomDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShockingRoomDatabase::class.java,
                    "user_database"
                ).fallbackToDestructiveMigration().build()
//                    .addCallBack(UserDatabaseCallback(scope))
//                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

    private class UserDatabaseCallback(private val scope: CoroutineScope) : Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.d("DatabaseCallback", "Database created")
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.shockingDao())
                }
            }
        }

        suspend fun populateDatabase(shockingDao : ShockingDao) {
            // Delete all content here.
            shockingDao.deleteAll()

            // Add sample words.
            var data = ShockingData(null, "123", "1","55AA3545", "DC", "5", "3", "3", "7", "8", "2", "9", "-", "-", false, false, "15")
            shockingDao.insert(data)
            data = ShockingData(null, "123", "1","55AA3545", "DC", "5", "3", "3", "7", "8", "2", "9", "-", "-", false, false, "15")
            shockingDao.insert(data)

        }
    }

}