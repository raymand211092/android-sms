package com.beeper.sms.provider

import com.beeper.sms.Log
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.database.models.InboxPreviewCacheDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class EntityChange<T> {
    data class Update<T>(val entity: T) : EntityChange<T>()
    data class Delete<T>(val entity: T) : EntityChange<T>()
    class Clear<T> : EntityChange<T>()
}

class InboxPreviewProvider constructor(
    private val inboxPreviewCacheDao: InboxPreviewCacheDao,
    val chatThreadProvider: ChatThreadProvider,
    val messageProvider: MessageProvider,

) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val mutPreviewCacheChanges = MutableSharedFlow<EntityChange<InboxPreviewCache>>()
    val previewCacheChanges = mutPreviewCacheChanges.asSharedFlow()

    fun markMessagesInThreadAsRead(messageId: String) {
        Log.d(TAG, "InboxPreview markMessagesInThreadAsRead messageId: $messageId")
        messageProvider.markMessageAsRead(messageId)

        val previewForMessageId = inboxPreviewCacheDao.getPreviewForMessage(messageId)
        if(previewForMessageId != null){
            Log.d(TAG, "InboxPreview markMessagesInThreadAsRead " +
                    "updating preview to read. chat_guid: ${previewForMessageId.chat_guid}")
            update(
                previewForMessageId.copy(
                    is_read = true
                )
            )
        }else{
            Log.d(TAG, "InboxPreview markMessagesInThreadAsRead read message " +
                    "is not the same as the preview $previewForMessageId $messageId")

        }
    }


    fun update(inboxPreviewCache: InboxPreviewCache) {
        coroutineScope.launch {
            // Update the recipient list when we update a preview cache
            val lastInboxPreview = inboxPreviewCacheDao.getPreviewForChat(
                inboxPreviewCache.chat_guid
            )
            Log.d(TAG, "InboxPreview cache debug: update lastInboxPreview threadID:" +
                    " ${lastInboxPreview?.thread_id}")

            val recipientIds =
                lastInboxPreview?.recipient_ids ?: chatThreadProvider.getThreadRecipients(
                    inboxPreviewCache.thread_id
                )

            Log.d(TAG, "InboxPreview cache debug: recipientIds: $recipientIds")
            val inboxPreviewWithRecipients =
                inboxPreviewCache.copy(
                    recipient_ids = recipientIds
                )
            Log.d(
                TAG, "InboxPreview cache debug: inboxPreviewWithRecipients threadId:" +
                        " ${inboxPreviewWithRecipients.thread_id}"
            )

            inboxPreviewCacheDao.insert(inboxPreviewWithRecipients)
            mutPreviewCacheChanges.emit(
                EntityChange.Update(
                    inboxPreviewWithRecipients
                )
            )
        }
    }

    fun delete(inboxPreviewCache: InboxPreviewCache) {
        coroutineScope.launch {
            inboxPreviewCacheDao.delete(inboxPreviewCache)
            mutPreviewCacheChanges.emit(
                EntityChange.Delete(
                    inboxPreviewCache
                )
            )
        }
    }

    fun clearAll() {
        coroutineScope.launch {
            inboxPreviewCacheDao.clear()
            mutPreviewCacheChanges.emit(
                EntityChange.Clear()
            )
        }
    }

    fun loadChatPreview(threadId: Long, mapMessageToInboxPreview: (Message)->InboxPreviewCache): InboxPreviewCache? {
        return runBlocking {
            loadChatPreviews(listOf(threadId), mapMessageToInboxPreview).firstOrNull()
        }
    }

    private suspend fun loadChatPreviews(threadIds: List<Long>, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        val previews = mutableListOf<InboxPreviewCache>()
        threadIds.onEach { threadId ->
            val cachedChatThread = inboxPreviewCacheDao.getPreviewForChatByThreadId(threadId)
            if (cachedChatThread != null) {
                Log.d(TAG, "InboxPreview cache debug: returning cached threadId:$threadId")
                previews.add(cachedChatThread)
            } else {
                Log.d(
                    TAG,
                    "InboxPreview cache debug: didn't find threadId:$threadId in cache -> loading in Android's DB"
                )
                val chatThread = chatThreadProvider.getThread(threadId)
                if (chatThread != null) {
                    val lastMessage = messageProvider.getLastMessage(
                        threadId
                    )
                    if (lastMessage != null) {
                            val newPreview = mapMessageToInboxPreview(
                                lastMessage
                            )
                            Log.d(
                                TAG, "InboxPreview cache debug: inserting new inbox" +
                                        " preview threadId:${newPreview.thread_id}"
                            )
                            update(newPreview)
                            previews.add(newPreview)
                    } else {
                        Log.e(TAG, "InboxPreview cache debug: null lastMessage ${chatThread.recipientIds}" +
                                "title: ${chatThread.getTitleFromMembers()} ")
                    }
                } else {
                    Log.e(TAG, "InboxPreview cache debug: null chatThread")
                }
            }
        }
        return previews
    }

    suspend fun getChatsAfter(timestamp: Long, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        return withContext(Dispatchers.IO) {
            val ids = chatThreadProvider.getChatIdsAfter(TimeMillis(timestamp.toBigDecimal()))
            loadChatPreviews(ids,mapMessageToInboxPreview)
        }
    }

    suspend fun getChatsBefore(timestamp: Long, limit: Int, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        return withContext(Dispatchers.IO) {
            val ids =
                chatThreadProvider.getChatIdsBefore(TimeMillis(timestamp.toBigDecimal()), limit)
            loadChatPreviews(ids,mapMessageToInboxPreview)
        }
    }


    suspend fun fetchThreads(limit: Int, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        return withContext(Dispatchers.IO) {
            val ids = chatThreadProvider.fetchIds(limit)
            loadChatPreviews(ids,mapMessageToInboxPreview)
        }
    }

    companion object {
        val TAG = "InboxPreviewProvider"
    }
}
