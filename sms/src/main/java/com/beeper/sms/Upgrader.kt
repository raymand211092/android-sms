package com.beeper.sms

import android.content.Context
import com.beeper.sms.extensions.getSharedPreferences
import com.beeper.sms.extensions.putLong
import com.beeper.sms.work.DatabaseSyncWork

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
        upgrade(from, 158) {
            /*
             * v158 adds new database sync logic
             */
            preferences.putLong(DatabaseSyncWork.PREF_LATEST_SYNC, System.currentTimeMillis())
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
        const val PREF_USE_OLD_MMS_GUIDS = "use_old_mms_guids"
    }
}
