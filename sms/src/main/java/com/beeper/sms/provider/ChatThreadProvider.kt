package com.beeper.sms.provider

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.core.text.isDigitsOnly
import com.beeper.sms.BuildConfig
import com.beeper.sms.Log
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


data class ChatThreadSummary(
    val timestamp: TimeMillis,
    val preview: String,
    val hasUnread: Boolean
)


class ChatThreadProvider constructor(
    val context: Context,
) {

    // Fetch all chats after an ID -> to be bridged
    suspend fun getValidThreadIdsAfter(initialId: Long): List<Long> {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")
            val projection = arrayOf(
                Telephony.Threads._ID,
                Telephony.Threads.MESSAGE_COUNT,
                )
            val selection = "${Telephony.Threads._ID} >= ? AND ${Telephony.Threads.MESSAGE_COUNT} > ?"
            val selectionArgs: Array<String> = arrayOf(initialId.toString(), "0")
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

    data class ThreadInfo(
        var threadId: String,
        var snippet: String,
        var timestamp: TimeMillis,
        var hasUnread: Boolean
    )

    suspend fun getChatsAfter(timestamp: TimeMillis): List<ChatThread> {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")
            val projection = arrayOf(
                Telephony.Threads._ID,
                Telephony.Threads.READ,
                Telephony.Threads.DATE,
                Telephony.Threads.SNIPPET,
                Telephony.Threads.RECIPIENT_IDS
            )
            val selection = "${Telephony.Threads.DATE} > ? AND ${Telephony.Threads.MESSAGE_COUNT} > ?"
            val selectionArgs: Array<String> = arrayOf(timestamp.toLong().toString(), "0")
            val sortOrder = "${Telephony.Threads.DATE} DESC"
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            val threads = mutableListOf<ChatThread>()
            cursor?.use {
                val contactProvider = ContactProvider(context)
                while (it.moveToNext()) {
                    val id = it.getLong(Telephony.Threads._ID)
                    val hasUnread = it.getInt(Telephony.Threads.READ) == 0

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
                                hasUnread
                            )
                        )
                    }
                }
            }
            threads
        }
    }

    suspend fun getChatsBefore(timestamp: TimeMillis, limit: Int): List<ChatThread> {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")
            val projection = arrayOf(
                Telephony.Threads._ID,
                Telephony.Threads.READ,
                Telephony.Threads.DATE,
                Telephony.Threads.SNIPPET,
                Telephony.Threads.RECIPIENT_IDS
            )
            val selection = "${Telephony.Threads.DATE} < ? AND ${Telephony.Threads.MESSAGE_COUNT} > ?"
            val selectionArgs: Array<String> = arrayOf(timestamp.toLong().toString(), "0")
            val sortOrder = "${Telephony.Threads.DATE} DESC LIMIT $limit"
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            val threads = mutableListOf<ChatThread>()
            cursor?.use {
                val contactProvider = ContactProvider(context)
                fun canFetchNextThreadDetail() = if(limit > 0 ){
                    threads.size < limit
                }else{
                    true
                }
                while (it.moveToNext() && canFetchNextThreadDetail()) {
                    val id = it.getLong(Telephony.Threads._ID)
                    val hasUnread = it.getInt(Telephony.Threads.READ) == 0

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
                                hasUnread
                            )
                        )
                    }
                }
            }
            threads
        }
    }


    suspend fun fetchThreads(limit: Int): List<ChatThread> {
        return withContext(Dispatchers.IO) {
            getThreads(limit = limit)
        }
    }

    fun getOrCreateThreadId(recipients: Set<String>): Long {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "getOrCreateThreadId recipients: $recipients")
        }
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
                    val hasUnread = it.getInt(Telephony.Threads.READ) == 0

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
                        hasUnread
                    )
                }
            }
            null
        }
    }

    fun getThreadSummary(threadId: Long): ChatThreadSummary? {
            val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")

            val projection = arrayOf(
                Telephony.Threads._ID,
                Telephony.Threads.SNIPPET,
                Telephony.Threads.DATE,
                Telephony.Threads.READ,
            )

            val selection = "${Telephony.Threads._ID} = ?"
            val selectionArgs: Array<String> = arrayOf(threadId.toString())

            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val hasUnread = it.getInt(Telephony.Threads.READ) == 0

                    val snippet = it.getString(Telephony.Threads.SNIPPET)
                    var date = cursor.getLong(Telephony.Threads.DATE)
                    if (date.toString().length ==10) {
                        date *= 1000
                    }

                    return ChatThreadSummary(
                        TimeMillis(BigDecimal.valueOf(date)),
                        snippet ?: "",
                        hasUnread
                    )
                }
            }
        return null

    }

    suspend fun getReadStatus(threadIds: List<Long>): Map<Long,Boolean> {
        return withContext(Dispatchers.IO) {
            val result = mutableMapOf<Long,Boolean>()
            threadIds.onEach {
                threadId->
                val uri = Uri.parse("${Telephony.Threads.CONTENT_URI}?simple=true")
                val projection = arrayOf(
                    Telephony.Threads._ID,
                    Telephony.Threads.READ,
                )
                val selection = "${Telephony.Threads._ID} = ? AND ${Telephony.Threads.READ} <> 0"
                val selectionArgs: Array<String> = arrayOf(threadId.toString())
                val cursor =
                    context.contentResolver.query(uri, projection, selection, selectionArgs, null
                    )
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        val id = it.getLong(Telephony.Threads._ID)
                        val hasUnread = it.getInt(Telephony.Threads.READ) == 0
                        result[id] = hasUnread
                    }
                }
            }
            result.toMap()
        }
    }

    private suspend fun getThreads(threadId: Long? = null, limit: Int = 0): List<ChatThread> {
        return withContext(Dispatchers.IO) {
            Timber.d("SMSInbox getThreads: limit: $limit")

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
            var sortOrder = "${Telephony.Threads.DATE} DESC"
            if(limit > 0 )
                sortOrder += " LIMIT $limit"

            val threads = mutableListOf<ChatThread>()
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                val contactProvider = ContactProvider(context)
                fun canFetchNextThreadDetail() = if(limit > 0 ){
                    threads.size < limit
                }else{
                    true
                }
                while (it.moveToNext() && canFetchNextThreadDetail()) {
                    val id = it.getLong(Telephony.Threads._ID)
                    val hasUnread = it.getInt(Telephony.Threads.READ) == 0

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
                                hasUnread
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
