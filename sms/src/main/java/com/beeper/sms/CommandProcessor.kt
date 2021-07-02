package com.beeper.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.*
import com.beeper.sms.commands.outgoing.Message
import com.google.gson.Gson
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class CommandProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: Bridge,
) {
    fun handle(input: String) {
        val command = gson.fromJson(input, Command::class.java)
        val dataTree = gson.toJsonTree(command.data)
        when (command.command) {
            "get_chat" -> {
                val data = gson.fromJson(dataTree, GetChat::class.java)
                val recipients = data.recipientList
                bridge.send(
                    Command(
                        "response",
                        GetChat.Response(recipients.joinToString(), recipients),
                        command.id,
                    )
                )
            }
            "get_contact" -> {
                val data = gson.fromJson(dataTree, GetContact::class.java)
                bridge.send(
                    Command(
                        "response",
                        GetContact.Response(
                            nickname = data.user_guid,
                            phones = listOf(data.user_guid),
                        ),
                        command.id,
                    )
                )
            }
            "send_message" -> {
                val data = gson.fromJson(dataTree, SendMessage::class.java)
                val recipients = data.recipientList
                Transaction(context, settings)
                    .sendNewMessage(
                        com.klinker.android.send_message.Message(
                            data.text,
                            recipients.joinToString(" ")
                        ),
                        Telephony.Threads.getOrCreateThreadId(context, recipients.toSet()),
                        command,
                        null,
                    )
            }
            "send_media" -> {
                val data = gson.fromJson(dataTree, SendMedia::class.java)
                val recipients = data.recipientList
                val message =
                    com.klinker.android.send_message.Message("", recipients.toTypedArray())
                        .apply {
                            addMedia(
                                File(data.path_on_disk).readBytes(),
                                data.mime_type,
                                data.file_name
                            )
                        }
                val thread = Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
                Transaction(context, settings).sendNewMessage(message, thread, command, null)
            }
            "get_chats" -> {
//                val data = gson.fromJson(dataTree, GetChats::class.java)
                bridge.send(Command("response", ArrayList<String>(), command.id))
            }
            "get_recent_messages" -> {
//                val data = gson.fromJson(dataTree, GetRecentMessages::class.java)
                bridge.send(Command("response", ArrayList<Message>(), command.id))
            }
            "get_chat_avatar" -> {
                bridge.send(Command("response", null, command.id))
            }
            else -> {
                Log.e(TAG, "unhandled command: $command")
            }
        }
    }

    companion object {
        private const val TAG = "CommandProcessor"
        private val gson = Gson()
        private val settings = Settings().apply {
            deliveryReports = true
            useSystemSending = true
        }
    }
}