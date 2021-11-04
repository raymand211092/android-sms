package com.beeper.sms

import android.content.Context
import android.os.Bundle
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.Error
import com.beeper.sms.commands.incoming.*
import com.beeper.sms.commands.incoming.GetContact.Response.Companion.asResponse
import com.beeper.sms.extensions.getThread
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.provider.ThreadProvider
import com.google.gson.Gson
import com.google.gson.JsonElement

class CommandProcessor constructor(
    private val context: Context,
    private val contactProvider: ContactProvider = ContactProvider(context),
    private val bridge: Bridge = Bridge.INSTANCE,
    private val threadProvider: ThreadProvider = ThreadProvider(context),
    private val smsProvider: SmsProvider = SmsProvider(context),
    private val mmsProvider: MmsProvider = MmsProvider(context),
    private val smsMmsSender: SmsMmsSender = SmsMmsSender(context),
) {
    fun handle(input: String) {
        val command = gson.fromJson(input, Command::class.java)
        val dataTree = gson.toJsonTree(command.data)
        when (command.command) {
            "get_chat" -> {
                val recipients =
                    dataTree
                        .deserialize(GetChat::class.java)
                        .recipientList
                val room =
                    contactProvider
                        .getContacts(recipients)
                        .map { it.nickname }
                        .joinToString()
                bridge.send(
                    Command("response", GetChat.Response(room, recipients), command.id)
                )
            }
            "get_contact" -> {
                val data = dataTree.deserialize(GetContact::class.java)
                bridge.send(
                    Command(
                        "response",
                        contactProvider.getContact(data.user_guid).asResponse,
                        command.id
                    )
                )
            }
            "send_message" -> {
                if (!context.hasPermissions) {
                    noPermissionError(command.id!!)
                    return
                }
                val data = dataTree.deserialize(SendMessage::class.java)
                smsMmsSender.sendMessage(
                    data.text,
                    data.recipientList,
                    context.getThread(data),
                    Bundle().apply {
                        putInt(EXTRA_COMMAND_ID, command.id!!)
                    },
                )
            }
            "send_media" -> {
                if (!context.hasPermissions) {
                    noPermissionError(command.id!!)
                    return
                }
                val data = dataTree.deserialize(SendMedia::class.java)
                val recipients = data.recipientList
                smsMmsSender.sendMessage(
                    recipients,
                    data.path_on_disk,
                    data.mime_type,
                    data.file_name,
                    context.getThread(data),
                    Bundle().apply {
                        putInt(EXTRA_COMMAND_ID, command.id!!)
                    },
                )
            }
            "get_chats" -> {
                val data = dataTree.deserialize(GetChats::class.java)
                val recentMessages =
                    smsProvider
                        .getMessagesAfter(data.min_timestamp * 1000)
                        .plus(mmsProvider.getMessagesAfter(data.min_timestamp))
                bridge.send(
                    Command(
                        "response",
                        recentMessages
                            .mapNotNull { it.thread }
                            .toSet()
                            .mapNotNull { threadProvider.getChatGuid(it) },
                        command.id
                    )
                )
            }
            "get_messages_after" -> {
                val data = dataTree.deserialize(GetMessagesAfter::class.java)
                val messages =
                    threadProvider
                        .getMessagesAfter(context.getThread(data), data.timestamp)
                        .toMessages()
                bridge.send(Command("response", messages, command.id))
            }
            "get_recent_messages" -> {
                val data = dataTree.deserialize(GetRecentMessages::class.java)
                val messages =
                    threadProvider
                        .getRecentMessages(context.getThread(data), data.limit.toInt())
                        .toMessages()
                bridge.send(Command("response", messages, command.id))
            }
            "get_chat_avatar" -> {
                bridge.send(Command("response", null, command.id))
            }
            else -> {
                Log.e(TAG, "unhandled command: $command")
            }
        }
    }

    private fun <T> JsonElement.deserialize(c: Class<T>): T =
        gson.fromJson(this, c)
            .apply { Log.d(TAG, "receive: $this") }

    private fun List<Pair<Long, Boolean>>.toMessages() = mapNotNull { (id, mms) ->
        if (mms) mmsProvider.getMessage(id) else smsProvider.getMessage(id)
    }

    private fun noPermissionError(commandId: Int) {
        bridge.send(
            Error(
                commandId,
                Error.Reason(
                    "no_permission",
                    context.getString(
                        R.string.missing_sms_permissions,
                        context.getString(R.string.app_name)
                    )
                )
            )
        )
    }

    companion object {
        private const val TAG = "CommandProcessor"
        private val gson = Gson()
        const val EXTRA_COMMAND_ID = "extra_command_id"
    }
}