package com.beeper.sms.app.ui.util

import android.content.Context
import android.text.format.DateUtils

fun formatMessageDate(timestamp: Long, context: Context): String {
    return if (DateUtils.isToday(timestamp)) {
        DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
    } else {
        DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE) + " at " +
                DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
    }
}

fun formatInboxDate(timestamp: Long, context: Context) : String{
    return if(DateUtils.isToday(timestamp)){
        DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
    }else{
        DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE)
    }
}
