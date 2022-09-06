package com.beeper.sms

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import com.beeper.sms.Upgrader.Companion.PREF_USE_OLD_MMS_GUIDS
import com.beeper.sms.Upgrader.Companion.PREF_USE_OLD_SMS_GUIDS
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.*
import com.beeper.sms.commands.incoming.GetContact.Response.Companion.asResponse
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.commands.outgoing.PushKey
import com.beeper.sms.extensions.getSharedPreferences
import com.beeper.sms.extensions.getThread
import com.beeper.sms.extensions.getTimeMilliseconds
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.helpers.newGson
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.MmsProvider.Companion.MMS_PREFIX
import com.beeper.sms.provider.SmsProvider.Companion.SMS_PREFIX
import com.google.gson.JsonElement
import com.klinker.android.send_message.Transaction.COMMAND_ID
import java.io.File

class CommandProcessor constructor(
    private val context: Context,
    private val pushKey: PushKey?,
    private val contactProvider: ContactProvider = ContactProvider(context),
    private val bridge: Bridge = Bridge.INSTANCE,
    private val messageProvider: MessageProvider = MessageProvider(context),
    private val smsMmsSender: SmsMmsSender = SmsMmsSender(context),
) {
    private val oldMmsBackfillSeconds =
        context
            .getSharedPreferences()
            .getTimeMilliseconds(PREF_USE_OLD_MMS_GUIDS)
            .toSeconds()
    private val oldSmsBackfillSeconds =
        context
            .getSharedPreferences()
            .getTimeMilliseconds(PREF_USE_OLD_SMS_GUIDS)
            .toSeconds()


    fun handle(input: String) {
        Log.v(TAG, input)
        val command = gson.fromJson(input, Command::class.java)
        when (command.command) {
            "pre_startup_sync" -> {
                Log.d(TAG, "receive: $command")
                pushKey?.let { bridge.send(Command("push_key", it)) }
                bridge.send(Command("response", null, command.id))
            }
            "get_chat" -> {
                val getChatCommand = command.deserialize(GetChat::class.java)
                val recipients = getChatCommand
                    .recipientList
                val room =
                    contactProvider
                        .getRecipients(recipients)
                        .map {
                            it.first.nickname
                        }
                        .joinToString()
                val threadId = context.getThread(getChatCommand)

                bridge.send(
                    Command("response", GetChat.Response(
                        room,
                        recipients,
                        threadId.toString()
                    ), command.id)
                )
            }
            "get_contact" -> {
                val data = command.deserialize(GetContact::class.java)
                bridge.send(
                    Command(
                        "response",
                        contactProvider.getRecipientInfo(data.user_guid).first.asResponse,
                        command.id
                    )
                )
            }
            "send_message" -> {
                if (!context.hasPermissions) {
                    noPermissionError(command.id!!)
                    return
                }
                val data = command.deserialize(SendMessage::class.java)
                smsMmsSender.sendMessage(
                    data.text,
                    data.recipientList,
                    context.getThread(data),
                    Bundle().apply {
                        putInt(COMMAND_ID, command.id!!)
                    },
                )
            }
            "send_media" -> {
                if (!context.hasPermissions) {
                    noPermissionError(command.id!!)
                    return
                }
                val data = command.deserialize(SendMedia::class.java)
                val recipients = data.recipientList
                val file = File(data.path_on_disk)
                val size = file.length()
                if (size > SmsMmsSender.MAX_FILE_SIZE) {
                    bridge.send(
                        command.id!!,
                        Error(
                            "size_limit_exceeded",
                            context.getString(
                                R.string.attachment_too_large,
                                formatShortFileSize(context, size),
                                formatShortFileSize(context, SmsMmsSender.MAX_FILE_SIZE),
                            )
                        )
                    )
                } else {
                    smsMmsSender.sendMessage(
                        data.text,
                        recipients,
                        file.readBytes(),
                        data.mime_type,
                        data.file_name,
                        context.getThread(data),
                        Bundle().apply {
                            putInt(COMMAND_ID, command.id!!)
                        },
                    )
                }
            }
            "get_chats" -> {
                val data = command.deserialize(GetChats::class.java)
                val recentMessages = messageProvider.getActiveChats(data.min_timestamp)
                bridge.send(
                    Command(
                        "response",
                        recentMessages.map { it.chat_guid }.toSet(),
                        command.id
                    )
                )
            }
            "get_messages_after" -> {
                val data = command.deserialize(GetMessagesAfter::class.java)
                val messages =
                    messageProvider
                        .getMessagesAfter(context.getThread(data), data.timestamp)
                        .filter {
                            // hack to not backfill rooms with invalid chat_guid
                            it.chat_guid == data.chat_guid
                        }
                if (data.timestamp < oldMmsBackfillSeconds) {
                    messages
                        .filter { it.timestamp < oldMmsBackfillSeconds }
                        .forEach { it.guid = it.guid.removePrefix(MMS_PREFIX) }
                }
                if (data.timestamp < oldSmsBackfillSeconds) {
                    messages
                        .filter { it.timestamp < oldSmsBackfillSeconds }
                        .forEach { it.guid = it.guid.removePrefix(SMS_PREFIX) }
                }
                bridge.send(Command("response", messages, command.id))
            }
            "get_recent_messages" -> {
                val data = command.deserialize(GetRecentMessages::class.java)
                val messages =
                    messageProvider.getRecentMessages(context.getThread(data), data.limit.toInt())
                bridge.send(Command("response", messages, command.id))
            }
            "get_chat_avatar" -> {
                bridge.send(Command("response", null, command.id))
            }
            "response" -> {
                Log.d(TAG, "response #${command.id}: ${command.dataTree}")
                if (command.id != null) {
                    bridge.publishResponse(command.id!!, command.dataTree)
                }
            }
            "send_read_receipt" -> {
                Log.v(TAG, "ignore command: $command")
            }
            else -> {
                Log.e(TAG, "unhandled command: $command")
            }
        }
    }

    private val Command.dataTree: JsonElement
        get() = gson.toJsonTree(data)

    private fun <T> Command.deserialize(c: Class<T>): T =
        gson.fromJson(dataTree, c)
            .apply { Log.d(TAG, "receive #$id: $this") }

    private fun noPermissionError(commandId: Int) {
        bridge.send(
            commandId,
            Error(
                "no_permission",
                context.getString(
                    R.string.missing_sms_permissions,
                    context.getString(R.string.app_name)
                )
            )
        )
    }

    companion object {
        private const val TAG = "CommandProcessor"
        private val gson = newGson()
    }
}