package com.beeper.sms.database.models

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.ReadReceipt

@Entity(primaryKeys = ["canonical_address_id"])
data class PendingContactUpdate(
    val canonical_address_id: Long,
    var first_name: String? = null,
    var last_name: String? = null,
    var nickname: String? = null,
    var avatarUri: String? = null,
    var phoneNumber: String? = null,
)
