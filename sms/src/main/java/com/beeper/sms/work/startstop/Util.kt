package com.beeper.sms.work.startstop

import android.content.Context
import android.os.Build
import androidx.work.ForegroundInfo
import com.beeper.sms.StartStopBridge

fun getDefaultForegroundInfo(context: Context, contentText: String): ForegroundInfo {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        StartStopBridge.INSTANCE.createNotificationChannel(
            context, StartStopBridge.DEFAULT_CHANNEL_ID)
    }
    return ForegroundInfo(
        StartStopBridge.ONGOING_NOTIFICATION_ID,
        StartStopBridge.INSTANCE.buildNotification(context,
            StartStopBridge.DEFAULT_CHANNEL_ID,
            contentText)
    )
}