package com.beeper.sms.provider

import android.content.Context
import android.provider.Telephony.*
import android.telephony.PhoneNumberUtils
import android.util.Patterns
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.SmsThreadMatrixRoomRelation
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getString
import java.net.URLEncoder
import java.util.*

class SMSThreadRoomRelationProvider constructor(
    context: Context,
) {
    private val smsThreadMatrixRoomRelationDao =
        BridgeDatabase.getInstance(context).nativeThreadMatrixRoomRelationDao()

    fun getThreadIdFromRoomId(roomId:String) : Long?{
        return smsThreadMatrixRoomRelationDao.getRelationsForRoom(roomId).maxByOrNull {
            it.timestamp
        }?.thread_id
    }

    fun getRoomIdFromThreadId(threadId:Long) : String?{
        return smsThreadMatrixRoomRelationDao.getRelationsForThread(threadId).maxByOrNull {
            it.timestamp
        }?.room_id
    }

    fun insert(relation: SmsThreadMatrixRoomRelation) {
        smsThreadMatrixRoomRelationDao.insert(relation)
    }
}