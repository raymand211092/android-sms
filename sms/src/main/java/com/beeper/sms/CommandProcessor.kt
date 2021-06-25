package com.beeper.sms

import android.content.Context
import android.content.Intent
import android.util.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.*
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.receivers.MyDeliveredReceiver
import com.beeper.sms.receivers.MyMmsSentReceiver
import com.beeper.sms.receivers.MySentReceiver
import com.google.gson.Gson
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CommandProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun handle(input: String): Command? {
        val command = gson.fromJson(input, Command::class.java)
        val dataTree = gson.toJsonTree(command.data)
        when (command.command) {
            "get_chat" -> {
                val data = gson.fromJson(dataTree, GetChat::class.java)
                if (data.chat_guid.startsWith("SMS;-;")) {
                    val dm = data.chat_guid.removePrefix("SMS;-;")
                    return Command(
                        "response",
                        GetChat.Response(dm, listOf(dm)),
                        command.id,
                    )
                } else {
                    Log.e(TAG, "group chats not supported yet")
                }
            }
            "get_contact" -> {
                val data = gson.fromJson(dataTree, GetContact::class.java)
                return Command(
                    "response",
                    GetContact.Response(
                        nickname = data.user_guid,
                        phones = listOf(data.user_guid),
                    ),
                    command.id,
                )
            }
            "send_message" -> {
                val data = gson.fromJson(dataTree, SendMessage::class.java)
                if (data.chat_guid.startsWith("SMS;-;")) {
                    val dm = data.chat_guid.removePrefix("SMS;-;")
                    transaction.sendNewMessage(
                        com.klinker.android.send_message.Message(data.text, dm),
                        Transaction.NO_THREAD_ID,
                        command,
                        command,
                    )
                } else {
                    Log.e(TAG, "group chats not supported yet")
                }
            }
            "get_chats" -> {
                val data = gson.fromJson(dataTree, GetChats::class.java)
                return Command(
                    "response",
                    ArrayList<String>(),
                    command.id,
                )
            }
            "get_recent_messages" -> {
                val data = gson.fromJson(dataTree, GetRecentMessages::class.java)
                return Command(
                    "response",
                    ArrayList<Message>(),
                    command.id,
                )
            }
            else -> {
                Log.e(TAG, "unhandled command: $command")
            }
        }
        return null
    }

    private val transaction: Transaction
        get() =
            Transaction(context, settings)
                .setExplicitBroadcastForSentMms(Intent(context, MyMmsSentReceiver::class.java))
                .setExplicitBroadcastForSentSms(Intent(context, MySentReceiver::class.java))
                .setExplicitBroadcastForDeliveredSms(
                    Intent(context, MyDeliveredReceiver::class.java)
                )

    companion object {
        private const val TAG = "CommandProcessor"
        private val gson = Gson()
        private val settings = Settings().apply {
            deliveryReports = true
            useSystemSending = true
        }
    }
}