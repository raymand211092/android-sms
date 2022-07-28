package com.beeper.sms.database.models

import com.beeper.sms.BuildConfig
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.provider.ContactRow
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid

data class ChatThread(
    var threadId: String,
    var chatGuid: String,
    var snippet: String,
    var members: Map<String,ContactRow>,
    var timestamp: TimeMillis,
    var hasUnread: Boolean
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

    override fun toString(): String {
        return "ChatThread(threadId='$threadId', snippet='${if (BuildConfig.DEBUG) snippet else "<redacted>"}', members=$members, timestamp=$timestamp, hasUnread=$hasUnread)"
    }
}