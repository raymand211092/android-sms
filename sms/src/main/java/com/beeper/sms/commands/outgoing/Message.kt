package com.beeper.sms.commands.outgoing

import com.beeper.sms.BuildConfig

data class Message(
    var guid: String,
    var timestamp: Long,
    var subject: String,
    var text: String,
    var chat_guid: String,
    var sender_guid: String?,
    var is_from_me: Boolean = false,
    var thread_originator_guid: String? = null,
    var thread_originator_part: Int? = null,
    var attachments: List<Attachment>? = null,
    var associated_message: List<AssociatedMessage>? = null,
    var group_action_type: Int? = null,
    var new_group_title: String? = null,
) {
    data class Attachment(
        var mime_type: String?,
        var file_name: String,
        var path_on_disk: String,
    )

    data class AssociatedMessage(
        var target_guid: String,
        var type: Int,
    )

    override fun toString(): String {
        return "Message(guid='$guid', timestamp=$timestamp, subject='${if (BuildConfig.DEBUG) subject else "<redacted>"}', text='${if (BuildConfig.DEBUG) text else "<redacted>"}', chat_guid='$chat_guid', sender_guid=$sender_guid, is_from_me=$is_from_me, thread_originator_guid=$thread_originator_guid, thread_originator_part=$thread_originator_part, attachments=$attachments, associated_message=$associated_message, group_action_type=$group_action_type, new_group_title=$new_group_title)"
    }
}
