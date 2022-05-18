package com.beeper.sms.database.models

import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.provider.ContactRow
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid

data class ChatThread(
    var threadId: String,
    var snippet: String,
    var members: Map<String,ContactRow>,
    var timestamp: TimeMillis,
    var isRead: Boolean
){
    fun getTitleFromMembers() : String{
        val threadMemberNames = members.map {
            val contactRow = it.value
            contactRow.nickname ?:
            contactRow.last_name ?:
            contactRow.phoneNumber ?:
            it.key
        }
        return if(threadMemberNames.isEmpty()){
            "Unknown contact"
        }else{
            threadMemberNames.reduce { acc, s -> "$acc, $s" }
        }
    }

    fun getChatGuid() : String?{
        return members.values.mapNotNull {
            it.phoneNumber?.chatGuid
        }.let {
            if (it.isEmpty()) {
                null
            } else {
                it.first()
            }
        }
    }



}