package com.beeper.sms.database.models

import androidx.room.Entity

@Entity(primaryKeys = ["canonical_address_id"])
data class ContactCache(
    val canonical_address_id: Long,
    var phoneNumber: String?,
    var phoneType: String?,
    var first_name: String?,
    var middle_name: String?,
    var last_name: String?,
    var nickname: String?,
    var avatarUri: String?,
){
    fun getDisplayName() : String{
        return nickname ?:
        last_name ?:
        phoneNumber ?: "#$canonical_address_id"
    }
}
