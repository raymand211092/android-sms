package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.Index

@Entity(primaryKeys = ["recipient_id"], indices = [
    Index(value = ["contact_id"]),
    Index(value = ["phone"])
])
data class RecipientCache(
        val recipient_id: Long,
        var contact_id: Long?,
        var phone: String?,
        var first_name: String?,
        var middle_name: String?,
        var last_name: String?,
        var nickname: String?,
    ){
    fun getDisplayName() : String{
        return nickname ?:
        last_name ?:
        phone ?: "#$recipient_id"
    }
}
