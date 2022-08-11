package com.beeper.sms.database.models

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index

@Entity(primaryKeys = ["contact_id"])
data class ContactInfoCache(
        val contact_id: Long,
        var display_name: String?,
        var starred: Boolean = false,
        var phone_numbers: String?,
)
