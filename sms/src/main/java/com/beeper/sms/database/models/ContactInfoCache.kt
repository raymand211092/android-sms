package com.beeper.sms.database.models

import androidx.room.Entity

@Entity(primaryKeys = ["contact_id"])
data class ContactInfoCache(
        val contact_id: Long,
        var display_name: String?,
        var starred: Boolean = false,
        var phone_numbers: String?,
        val phone_types: String?
)
