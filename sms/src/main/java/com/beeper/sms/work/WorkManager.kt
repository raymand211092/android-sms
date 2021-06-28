package com.beeper.sms.work

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WorkManager @Inject constructor(@ApplicationContext context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun sendSms(uri: Uri) =
        OneTimeWorkRequest
            .Builder(SendSms::class.java)
            .setInputData(Data.Builder().putString(SendSms.URI, uri.toString()).build())
            .build()
            .apply { workManager.enqueue(this) }
}