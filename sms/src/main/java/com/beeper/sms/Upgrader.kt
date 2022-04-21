package com.beeper.sms

import android.content.Context
import com.beeper.sms.extensions.*
import com.beeper.sms.helpers.currentTimeMillis
import com.beeper.sms.helpers.currentTimeSeconds
import com.beeper.sms.work.DatabaseSyncWork
import java.io.File

class Upgrader(private val context: Context) {
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
             * Backfilling messages sent before this change need to use the old guid
             * to prevent sending duplicate messages
             */
            preferences.putTimeMilliseconds(PREF_USE_OLD_MMS_GUIDS, currentTimeMillis())
        }
        upgrade(from, 159) {
            /*
             * v159 adds new database sync logic
             */
            preferences.putTimeSeconds(DatabaseSyncWork.PREF_LATEST_SYNC, currentTimeSeconds())
        }
        upgrade(from, 162) {
            /*
             * v162 adds an "sms_" prefix to SMS guids to prevent clashing with MMS
             * Backfilling messages sent before this change need to use the old guid
             * to prevent sending duplicate messages
             */
            preferences.putTimeMilliseconds(PREF_USE_OLD_SMS_GUIDS, currentTimeMillis())
        }
        upgrade(from, 185) {
            Log.d(TAG, "Deleting cached mms attachments")
            File(context.mmsCache).deleteRecursively()
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
        const val PREF_USE_OLD_SMS_GUIDS = "use_old_sms_guids"
    }
}
