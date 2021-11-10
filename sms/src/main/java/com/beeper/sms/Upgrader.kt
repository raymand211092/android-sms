package com.beeper.sms

import android.content.Context
import com.beeper.sms.extensions.*

class Upgrader(context: Context) {
    private val preferences = context.getSharedPreferences()

    fun upgrade() {
        val from = preferences.getLong(PREF_CURRENT_VERSION, 0L)
        val to = BuildConfig.BRIDGE_VERSION
        if (from == to) {
            return
        }
        Log.d(TAG, "Beginning upgrade from $from to $to")
        upgrade(from, 140) {
            /*
             * v140 adds an "mms_" prefix to MMS guids to prevent clashing with SMS
             * This can lead to duplicate messages when the latest message in a thread
             * was an MMS sent before this change
             */
            preferences.putLong(PREF_USE_OLD_MMS_GUIDS, System.currentTimeMillis())
        }
        upgrade(from, 143) {
            /*
             * prior to v143 chat guids were generated in database insertion order.
             * when migrating to a new device the chat guid could change and create a new room.
             * uninstall/reinstalls also created duplicate rooms before portal recovery
             *
             * portal recovery started backfilling these old rooms on startup, making them appear
             * recently active at the top of the conversation list. but these duplicate rooms would
             * not function correctly because new messages only flowed into the current chat guid
             *
             * when this pref is false the bridge will send chat_id commands to change all threads
             * to sorted chat guids. when mautrix-imessage starts backfilling individual rooms it
             * will also send chat_id requests if the chat guid is not sorted. if the backfill is
             * for a duplicate room then mautrix-imessage will leave the room
             */
            preferences.putBoolean(PREF_FIXED_ROOM_IDS, false)
        }
        preferences.putLong(PREF_CURRENT_VERSION, to)
        Log.d(TAG, "Finished upgrade from $from to $to")
    }

    private fun upgrade(from: Long, version: Long, doUpgrade: () -> Unit) {
        if (from < version) {
            Log.d(TAG, "Applying upgrade for $version")
            doUpgrade()
            preferences.putLong(PREF_CURRENT_VERSION, version)
        }
    }

    companion object {
        private const val TAG = "Upgrader"
        private const val PREF_CURRENT_VERSION = "current_version"
    }
}
