package com.beeper.sms

import android.content.Context
import android.util.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.Error
import com.beeper.sms.commands.incoming.*
import com.beeper.sms.commands.incoming.GetContact.Response.Companion.asResponse
import com.beeper.sms.extensions.getThread
import com.beeper.sms.extensions.isDefaultSmsApp
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.provider.ThreadProvider
import com.google.gson.Gson
import com.klinker.android.send_message.Transaction
import java.io.File

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
                    gson.fromJson(dataTree, GetChat::class.java)
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
                val data = gson.fromJson(dataTree, GetContact::class.java)
                bridge.send(
                    Command(
                        "response",
                        contactProvider.getContact(data.user_guid).asResponse,
                        command.id
                    )
                )
            }
            "send_message" -> {
                if (!context.isDefaultSmsApp) {
                    noPermissionError(command.id!!)
                    return
                }
                val data = gson.fromJson(dataTree, SendMessage::class.java)
                smsMmsSender.sendMessage(
                    data.text,
                    data.recipientList,
                    context.getThread(data),
                    command
                )
            }
            "send_media" -> {
                if (!context.isDefaultSmsApp) {
                    noPermissionError(command.id!!)
                    return
                }
                val data = gson.fromJson(dataTree, SendMedia::class.java)
                val recipients = data.recipientList
                smsMmsSender.sendMessage(
                    recipients,
                    data.path_on_disk,
                    data.mime_type,
                    data.file_name,
                    context.getThread(data),
                    command
                )
            }
            "get_chats" -> {
                val data = gson.fromJson(dataTree, GetChats::class.java)
                bridge.send(
                    Command(
                        "response",
                        threadProvider.getRecentConversations(data.min_timestamp * 1000),
                        command.id
                    )
                )
            }
            "get_messages_after" -> {
                val data = gson.fromJson(dataTree, GetMessagesAfter::class.java)
                val messages =
                    threadProvider
                        .getMessagesAfter(context.getThread(data), data.timestamp)
                        .toMessages()
                bridge.send(Command("response", messages, command.id))
            }
            "get_recent_messages" -> {
                val data = gson.fromJson(dataTree, GetRecentMessages::class.java)
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
                        R.string.not_default_sms_app,
                        context.getString(R.string.app_name)
                    )
                )
            )
        )
    }

    companion object {
        private const val TAG = "CommandProcessor"
        private val gson = Gson()
    }
}