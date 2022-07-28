package com.beeper.sms.commands.outgoing

import android.net.Uri
import com.beeper.sms.BuildConfig
import com.beeper.sms.commands.TimeSeconds

sealed class MessageStatus{
    object Sent : MessageStatus()
    object Waiting : MessageStatus()
    object Failed : MessageStatus()
}

data class Message(
    var guid: String,
    var timestamp: TimeSeconds,
    var subject: String,
    var text: String,
    var chat_guid: String,
    @Transient var thread_id: Long,
    var sender_guid: String?,
    var is_from_me: Boolean = false,
    var thread_originator_guid: String? = null,
    var thread_originator_part: Int? = null,
    var attachments: List<Attachment>? = null,
    var associated_message: List<AssociatedMessage>? = null,
    var group_action_type: Int? = null,
    var new_group_title: String? = null,
    @Transient var is_mms: Boolean = false,
    @Transient var resp_st: Int? = null,
    @Transient var creator: String? = null,
    @Transient var rowId: Long,
    @Transient var uri: Uri? = null,
    @Transient var subId: Int? = null,
    @Transient var messageStatus: MessageStatus? = null,
    @Transient var is_read: Boolean? = null,
    ) {
    data class Attachment(
        var mime_type: String?,
        var file_name: String,
        var path_on_disk: String,
        @Transient var media_thumbnail_height: Int? = null,
        @Transient var media_thumbnail_width: Int? = null

    )

    data class AssociatedMessage(
        var target_guid: String,
        var type: Int,
    )

    override fun toString(): String {
        return "Message(guid='$guid', timestamp=$timestamp, subject='${if (BuildConfig.DEBUG) subject else "<redacted>"}', text='${if (BuildConfig.DEBUG) text else "<redacted>"}', chat_guid='$chat_guid', sender_guid=$sender_guid, is_from_me=$is_from_me, thread_originator_guid=$thread_originator_guid, thread_originator_part=$thread_originator_part, attachments=$attachments, associated_message=$associated_message, group_action_type=$group_action_type, new_group_title=$new_group_title, is_mms=$is_mms, resp_st=$resp_st, creator=$creator, rowId=$rowId, uri=$uri, subId=$subId)"
    }
}
