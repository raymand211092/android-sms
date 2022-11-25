package com.beeper.sms.provider

import android.content.Context
import android.content.Intent
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.database.models.InboxPreviewCacheDao
import com.beeper.sms.receivers.BackfillSuccess
import com.beeper.sms.receivers.SMSChatMarkedAsRead
import com.beeper.sms.receivers.SMSChatMarkedAsRead.Companion.THREAD_ID_EXTRA_KEY
import com.klinker.android.send_message.BroadcastUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
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
    private val mutPreviewCacheChanges = MutableSharedFlow<EntityChange<InboxPreviewCache>>(0, 100,BufferOverflow.DROP_OLDEST)
    val previewCacheChanges = mutPreviewCacheChanges.asSharedFlow()

    fun markThreadAsRead(threadId: Long) {
        Log.d(TAG, "markThreadAsRead threadId: $threadId")
        messageProvider.markConversationAsRead(threadId)

        val inboxPreviewCache = inboxPreviewCacheDao.getPreviewForChatByThreadId(threadId)
        if(inboxPreviewCache != null){
            Log.d(TAG, "markThreadAsRead " +
                    "updating preview to read. chat_guid: ${inboxPreviewCache.chat_guid}")
            update(
                inboxPreviewCache.copy(
                    is_read = true
                )
            )
        }else{
            Log.w(TAG, "markThreadAsRead couldn't mark $threadId as read")
        }
    }

    fun markMessagesInThreadAsRead(messageId: String, context: Context) {
        Log.d(TAG, "markMessagesInThreadAsRead messageId: $messageId")
        val threadId = messageProvider.markMessageAsRead(messageId)
        // Broadcast chat marked as read to dismiss SMS notifications
        val intent = Intent(SMSChatMarkedAsRead.ACTION)
        intent.putExtra(THREAD_ID_EXTRA_KEY, threadId.toString())
        BroadcastUtils.sendExplicitBroadcast(context, intent, SMSChatMarkedAsRead.ACTION)

        val previewForMessageId = inboxPreviewCacheDao.getPreviewForMessage(messageId)
        if(previewForMessageId != null){
            Log.d(TAG, "markMessagesInThreadAsRead " +
                    "updating preview to read. chat_guid: ${previewForMessageId.chat_guid}")
            update(
                previewForMessageId.copy(
                    is_read = true
                )
            )
        }else{
            Log.d(TAG, "markMessagesInThreadAsRead read message " +
                    "is not the same as the preview $previewForMessageId $messageId")
        }
    }


    fun update(inboxPreviewCache: InboxPreviewCache) {
        coroutineScope.launch {
            // Update the recipient list when we update a preview cache
            val lastInboxPreview = inboxPreviewCacheDao.getPreviewForChat(
                inboxPreviewCache.chat_guid
            )

            Log.v(TAG, "cache debug: update lastInboxPreview threadID:" +
                    " ${lastInboxPreview?.thread_id}")

            val recipientIds =
                lastInboxPreview?.recipient_ids ?: chatThreadProvider.getThreadRecipients(
                    inboxPreviewCache.thread_id
                )

            Log.v(TAG, "cache debug: recipientIds: $recipientIds")
            val inboxPreviewWithRecipients =
                inboxPreviewCache.copy(
                    recipient_ids = recipientIds
                )
            Log.v(
                TAG, "cache debug: inboxPreviewWithRecipients threadId:" +
                        " ${inboxPreviewWithRecipients.thread_id}"
            )

            inboxPreviewCacheDao.insert(inboxPreviewWithRecipients)
            mutPreviewCacheChanges.tryEmit(
                EntityChange.Update(
                    inboxPreviewWithRecipients
                )
            )
        }
    }

    fun delete(inboxPreviewCache: InboxPreviewCache) {
        coroutineScope.launch {
            inboxPreviewCacheDao.delete(inboxPreviewCache)
            mutPreviewCacheChanges.tryEmit(
                EntityChange.Delete(
                    inboxPreviewCache
                )
            )
        }
    }

    fun clearAll() {
        coroutineScope.launch {
            inboxPreviewCacheDao.clear()
            mutPreviewCacheChanges.tryEmit(
                EntityChange.Clear()
            )
        }
    }

    fun loadChatPreviewForInbox(threadId: Long): InboxPreviewCache? {
        Log.d(TAG, "cache debug: loadChatPreviewForInbox")
        return runBlocking {
            withContext(Dispatchers.IO) {
                inboxPreviewCacheDao.getPreviewForChatByThreadId(threadId)
            }
        }
    }

    fun loadChatPreview(threadId: Long, mapMessageToInboxPreview: (Message)->InboxPreviewCache): InboxPreviewCache? {
        Log.d(TAG, "cache debug: loadChatPreview")
        return runBlocking {
            loadChatPreviews(listOf(threadId), mapMessageToInboxPreview).firstOrNull()
        }
    }

    suspend fun onChatChange(
        threadId: Long,
        mapMessageToInboxPreview: (Message) -> InboxPreviewCache
    ){
        val cachedChatThread = inboxPreviewCacheDao.getPreviewForChatByThreadId(threadId)
        if(cachedChatThread!=null) {
            refreshCacheFor(threadId, cachedChatThread, mapMessageToInboxPreview)
        }
    }

    private suspend fun refreshCacheFor(
        threadId: Long,
        cachedChatThread: InboxPreviewCache,
        mapMessageToInboxPreview: (Message) -> InboxPreviewCache
    ){
        val chatThread = chatThreadProvider.getThread(threadId)
        if (chatThread != null) {
            val lastMessage = messageProvider.getLastMessage(
                threadId
            )
            if (lastMessage != null) {
                val newPreview = mapMessageToInboxPreview(
                    lastMessage
                )
                Log.v(
                    TAG, "cache debug: inserting new inbox" +
                            " preview threadId:${newPreview.thread_id} lastMessageTs, " +
                            "${lastMessage.timestamp.toMillis().toLong()} " +
                            "cachedMessageTs: ${cachedChatThread.timestamp}"
                )

                if(lastMessage.guid != cachedChatThread.message_guid){
                    Log.v(
                        TAG, "cache debug: updating" +
                                "cache for threadId:${newPreview.thread_id}"
                    )
                    update(newPreview)
                }else{
                    Log.v(
                        TAG, "cache debug: not updating" +
                                "cache for threadId:${newPreview.thread_id} -> nothing changed"
                    )
                }


            } else {
                Log.e(
                    TAG,
                    "cache debug: null lastMessage ${chatThread.recipientIds}" +
                            "title: ${chatThread.getTitleFromMembers()} "
                )
            }
        } else {
            Log.e(TAG, "cache debug: null chatThread")
        }
    }

    private suspend fun loadChatPreviews(threadIds: List<Long>, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        return withContext(Dispatchers.IO) {
            val previews = mutableListOf<InboxPreviewCache>()
            Log.d(TAG, "cache debug: Started load chat previews for: $threadIds")

            threadIds.map {
                threadId ->
                async {
                    val cachedChatThread = inboxPreviewCacheDao.getPreviewForChatByThreadId(threadId)
                    if (cachedChatThread != null) {
                        Log.v(TAG, " InboxPreview cache debug: returning cached threadId:$threadId")
                        previews.add(cachedChatThread)
                        mutPreviewCacheChanges.tryEmit(
                            EntityChange.Update(
                                cachedChatThread
                            )
                        )
                        launch(Dispatchers.Default) {
                            refreshCacheFor(threadId, cachedChatThread, mapMessageToInboxPreview)
                        }
                    } else {
                        Log.v(
                            TAG,
                            "cache debug: didn't find threadId:$threadId in cache -> loading in Android's DB"
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
                                Log.v(
                                    TAG, "cache debug: inserting new inbox" +
                                            " preview threadId:${newPreview.thread_id}"
                                )
                                update(newPreview)
                                previews.add(newPreview)
                            } else {
                                Log.e(
                                    TAG,
                                    "cache debug: null lastMessage ${chatThread.recipientIds}" +
                                            "title: ${chatThread.getTitleFromMembers()} "
                                )
                            }
                        } else {
                            Log.e(TAG, "cache debug: null chatThread")
                        }
                    }
                }
            }.onEach {
                it.await()
            }

            Log.v(TAG, "cache debug: Finished loading chat previews")
            return@withContext previews
        }
    }

    suspend fun getChatsAfter(timestamp: Long, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        Log.d(TAG, "cache debug: getChatsAfter $timestamp")

        return withContext(Dispatchers.IO) {
            val ids = chatThreadProvider.getChatIdsAfter(TimeMillis(timestamp.toBigDecimal()))
            loadChatPreviews(ids,mapMessageToInboxPreview)
        }
    }

    suspend fun getChatsBefore(offset: Int, limit: Int, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        Log.d(TAG, "cache debug: getChatsBefore  $offset")

        return withContext(Dispatchers.IO) {
            val ids =
                chatThreadProvider.getChatIdsBefore(offset = offset, limit = limit)
            Log.v(TAG, "getChatIdsBefore offset: $offset" +
                    " limit: $limit ids.size: ${ids.size}")

            loadChatPreviews(ids,mapMessageToInboxPreview)
        }
    }

    suspend fun fetchThreads(limit: Int, mapMessageToInboxPreview: (Message)->InboxPreviewCache): List<InboxPreviewCache> {
        return withContext(Dispatchers.IO) {
            val ids = chatThreadProvider.fetchIds(limit)
            Log.d(TAG, "fetchThreads limit: $limit ids.size: ${ids.size}")
            loadChatPreviews(ids,mapMessageToInboxPreview)
        }
    }

    companion object {
        val TAG = "InboxPreviewProvider"
    }
}
