package com.beeper.sms.database

import android.content.Context
import androidx.room.Room

object BridgeDatabase  {
    @Volatile private var INSTANCE: BridgedEntitiesDatabase? = null

    fun getInstance(context: Context): BridgedEntitiesDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
        }

    private fun buildDatabase(context: Context) =
        Room.databaseBuilder(
            context,
            BridgedEntitiesDatabase::class.java, "sms-bridged-entities"
        ).build()
}