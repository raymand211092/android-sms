package com.beeper.sms.database.models

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.ReadReceipt

@Entity(primaryKeys = ["recipient_id"])
data class PendingRecipientUpdate(
    val recipient_id: Long,
    var contact_id: Long?,
    var phone: String? = null,
    var first_name: String? = null,
    var middle_name: String?,
    var last_name: String? = null,
    var nickname: String? = null,
)
