package com.beeper.sms.provider

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.core.text.isDigitsOnly
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.database.models.ChatThread
import com.beeper.sms.extensions.getInt
import com.beeper.sms.extensions.getLong
import com.beeper.sms.extensions.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal

class ChatThreadProvider constructor(
    val context: Context,
) {

    // Fetch all chats after an ID -> to be bridged
    suspend fun getNewChatThreadIds(initialId: Long): List<Long> {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")
            val projection = arrayOf(
                Telephony.Threads._ID,
            )
            val selection = "${Telephony.Threads._ID} >= ?"
            val selectionArgs: Array<String> = arrayOf(initialId.toString())
            val sortOrder = "${Telephony.Threads._ID} ASC"
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            val threadIds = mutableListOf<Long>()
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(Telephony.Threads._ID)
                    threadIds.add(id)
                }
            }
            threadIds.toList()
        }
    }

    suspend fun fetchThreads(): List<ChatThread> {
        return withContext(Dispatchers.IO) {
            getThreads()
        }
    }

    fun getOrCreateThreadId(recipients: Set<String>): Long {
        return Telephony.Threads.getOrCreateThreadId(context, recipients)
    }

    suspend fun getThread(threadId: Long, includeEmpty: Boolean = false): ChatThread? {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")

            val projection = arrayOf(
                Telephony.Threads._ID,
                Telephony.Threads.SNIPPET,
                Telephony.Threads.DATE,
                Telephony.Threads.READ,
                Telephony.Threads.RECIPIENT_IDS
            )

            var selection = if(!includeEmpty) {
                "${Telephony.Threads.MESSAGE_COUNT} > ?"
            }else{
                "${Telephony.Threads.MESSAGE_COUNT} >= ?"
            }
            selection += " AND ${Telephony.Threads._ID} = ?"
            val selectionArgs: Array<String> = arrayOf("0", threadId.toString())


            val sortOrder = "${Telephony.Threads.DATE} DESC"
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                val contactProvider = ContactProvider(context)
                while (it.moveToNext()) {
                    val id = it.getLong(Telephony.Threads._ID)
                    val isRead = it.getInt(Telephony.Threads.READ) == 0

                    val snippet = it.getString(Telephony.Threads.SNIPPET)
                    var date = cursor.getLong(Telephony.Threads.DATE)
                    if (date.toString().length > 10) {
                        date /= 1000
                    }
                    val ids = cursor.getString(Telephony.Threads.RECIPIENT_IDS)


                    val recipientIdList = ids?.split(" ")?.map { recipientId ->
                        recipientId.toLong()
                    }?.toList()

                    val recipientPhoneNumbers = recipientIdList?.let { recipientIds ->
                        getThreadPhoneNumbers(recipientIds)
                    }

                    val contacts = recipientPhoneNumbers?.let { numbers ->
                        numbers.map {
                            number ->
                            number to contactProvider.getContact(number)
                        }
                    }?.toMap() ?: return@withContext null

                    return@withContext ChatThread(
                        id.toString(),
                        snippet ?: "",
                        contacts,
                        TimeMillis(BigDecimal.valueOf(date)),
                        isRead
                    )
                }
            }
            null
        }
    }

    private suspend fun getThreads(threadId: Long? = null): List<ChatThread> {
        return withContext(Dispatchers.IO) {

            val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")

            val projection = arrayOf(
                Telephony.Threads._ID,
                Telephony.Threads.SNIPPET,
                Telephony.Threads.DATE,
                Telephony.Threads.READ,
                Telephony.Threads.RECIPIENT_IDS
            )

            var selection = "${Telephony.Threads.MESSAGE_COUNT} > ?"
            var selectionArgs = arrayOf("0")
            if (threadId != null) {
                selection += " AND ${Telephony.Threads._ID} = ?"
                selectionArgs = arrayOf("0", threadId.toString())
            }

            val sortOrder = "${Telephony.Threads.DATE} DESC"
            val threads = mutableListOf<ChatThread>()
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                val contactProvider = ContactProvider(context)
                while (it.moveToNext()) {
                    val id = it.getLong(Telephony.Threads._ID)
                    val isRead = it.getInt(Telephony.Threads.READ) == 0

                    val snippet = it.getString(Telephony.Threads.SNIPPET)
                    var date = cursor.getLong(Telephony.Threads.DATE)
                    if (date.toString().length > 10) {
                        date /= 1000
                    }
                    val ids = cursor.getString(Telephony.Threads.RECIPIENT_IDS)
                    val recipientIdList = ids?.split(" ")?.filter {
                        recipient->
                        recipient.isDigitsOnly()
                    }?.map { number -> number.toLong() }?.toList()

                    val recipientPhoneNumbers = recipientIdList?.let { recipientIds ->
                        getThreadPhoneNumbers(recipientIds)
                    }

                    val contacts = recipientPhoneNumbers?.let { numbers ->
                        numbers.map {
                            number ->
                            number to contactProvider.getContact(number)
                        }
                    }?.toMap()

                    if (contacts != null) {
                        threads.add(
                            ChatThread(
                                id.toString(),
                                snippet ?: "",
                                contacts,
                                TimeSeconds(BigDecimal.valueOf(date)).toMillis(),
                                isRead
                            )
                        )
                    }
                }
            }
            threads
        }
    }

    private fun getThreadPhoneNumbers(recipientIds: List<Long>): ArrayList<String> {
        val numbers = ArrayList<String>()
        recipientIds.forEach {
            val number = getPhoneNumberFromAddressId(it)
            if (number != null) {
                numbers.add(number)
            }
        }
        return numbers
    }

    private fun getPhoneNumberFromAddressId(canonicalAddressId: Long): String? {
        val uri = Uri.withAppendedPath(Telephony.MmsSms.CONTENT_URI, "canonical-addresses")
        val projection = arrayOf(
            Telephony.Mms.Addr.ADDRESS
        )

        val selection = "${Telephony.Mms._ID} = ?"
        val selectionArgs = arrayOf(canonicalAddressId.toString())
        try {
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    return cursor.getString(Telephony.Mms.Addr.ADDRESS)
                }
            }
        } catch (e: Exception) {
            Timber.e("Error getting phone number")
        }
        return null
    }

}
