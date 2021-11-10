package com.beeper.sms

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.*
import com.beeper.sms.commands.incoming.GetContact.Response.Companion.asResponse
import com.beeper.sms.commands.outgoing.ChatId
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.extensions.PREF_USE_OLD_MMS_GUIDS
import com.beeper.sms.extensions.getSharedPreferences
import com.beeper.sms.extensions.getThread
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.provider.ThreadProvider
import com.beeper.sms.provider.ThreadProvider.Companion.chatGuid
import com.beeper.sms.provider.ThreadProvider.Companion.normalize
import com.google.gson.Gson
import com.google.gson.JsonElement
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
    private val oldBackfillSeconds =
        context.getSharedPreferences().getLong(PREF_USE_OLD_MMS_GUIDS, 0L) / 1000

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
                    context.getThread(data) ?: 0L,
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
                val file = File(data.path_on_disk)
                val size = file.length()
                if (size > MAX_FILE_SIZE) {
                    bridge.send(
                        command.id!!,
                        Error(
                            "size_limit_exceeded",
                            context.getString(
                                R.string.attachment_too_large,
                                formatShortFileSize(context, size),
                                formatShortFileSize(context, MAX_FILE_SIZE),
                            )
                        )
                    )
                } else {
                    smsMmsSender.sendMessage(
                        recipients,
                        file.readBytes(),
                        data.mime_type,
                        data.file_name,
                        context.getThread(data) ?: 0L,
                        Bundle().apply {
                            putInt(EXTRA_COMMAND_ID, command.id!!)
                        },
                    )
                }
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
                val messages = getThread(data)
                    ?.let {
                        threadProvider
                            .getMessagesAfter(it, data.timestamp)
                            .toMessages()
                    }
                    ?: emptyList()
                if (data.timestamp < oldBackfillSeconds) {
                    messages
                        .filter { it.timestamp < oldBackfillSeconds }
                        .forEach { it.guid = it.guid.removePrefix("mms_") }
                }
                bridge.send(Command("response", messages, command.id))
            }
            "get_recent_messages" -> {
                val data = dataTree.deserialize(GetRecentMessages::class.java)
                val messages =
                    getThread(data)
                        ?.let {
                            threadProvider
                                .getRecentMessages(it, data.limit.toInt())
                                .toMessages()
                        }
                        ?: emptyList()
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

    private fun getThread(data: GroupMessaging): Long? {
        val thread = context.getThread(data) ?: return null
        val numbers = threadProvider.getPhoneNumbers(thread) ?: return null
        val chatGuid = numbers.chatGuid
        if (data.chat_guid != chatGuid) {
            fixChatGuid(data.chat_guid, chatGuid)
        }
        return thread
    }

    private fun <T> JsonElement.deserialize(c: Class<T>): T =
        gson.fromJson(this, c)
            .apply { Log.d(TAG, "receive: $this") }

    private fun List<Pair<Long, Boolean>>.toMessages() = mapNotNull { (id, mms) ->
        if (mms) mmsProvider.getMessage(id) else smsProvider.getMessage(id)
    }

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

    fun fixChatGuids() {
        threadProvider.getThreads().forEach { thread ->
            val numbers =
                threadProvider.getPhoneNumbers(thread)?.takeIf { it.size > 1 } ?: return@forEach
            fixChatGuid(numbers.legacyChatGuid, numbers.chatGuid)
        }
    }

    private fun fixChatGuid(oldGuid: String, newGuid: String) {
        if (oldGuid != newGuid) {
            bridge.send(Command("chat_id", ChatId(oldGuid, newGuid)))
        }
    }

    companion object {
        private const val TAG = "CommandProcessor"
        private const val MAX_FILE_SIZE = 400_000L
        private val gson = Gson()
        const val EXTRA_COMMAND_ID = "extra_command_id"

        private val List<String>.legacyChatGuid: String
            get() = "SMS;${if (size == 1) "-" else "+"};${joinToString(" ") { it.normalize }}"
    }
}
