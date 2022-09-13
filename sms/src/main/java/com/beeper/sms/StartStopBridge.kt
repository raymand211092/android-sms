package com.beeper.sms

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.internal.BridgeReadReceipt
import com.beeper.sms.commands.internal.BridgeSendError
import com.beeper.sms.commands.internal.BridgeSendResponse
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.*
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.PendingReadReceipt
import com.beeper.sms.extensions.cacheDirPath
import com.beeper.sms.extensions.env
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.extensions.mmsDir
import com.beeper.sms.helpers.newGson
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicInteger

sealed class SyncWindowState{
    object Starting : SyncWindowState()

    object Running : SyncWindowState()

    object Stopping : SyncWindowState()

    object Stopped : SyncWindowState()
}

class StartStopBridge private constructor() {
    lateinit var commandProcessor : StartStopCommandProcessor
    private lateinit var nativeLibDir: String
    private var channelId: String = DEFAULT_CHANNEL_ID

    private var configPathProvider: (suspend () -> String?)? = null
    private var configPath: String? = null
    private var channelIcon: Int = R.drawable.ic_cloud
    private var pushKey: PushKey? = null
    private var process: Process? = null
        get() = field?.takeIf { it.running }
    private val outgoing = newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _commandsReceived = MutableSharedFlow<Command>(
            replay = 0,
        extraBufferCapacity = 500,
        BufferOverflow.DROP_OLDEST
    )
    val commandsReceived = _commandsReceived.asSharedFlow()

    internal val _workerExceptions = MutableSharedFlow<Pair<String,Exception>>(
        replay = 0,
        extraBufferCapacity = 10,
        BufferOverflow.DROP_OLDEST
    )
    val workerExceptions = _workerExceptions.asSharedFlow()

    private val requestId = AtomicInteger(0)

    private var errorHandling: Job? = null
    private var commandHandling: Job? = null
    private val COMMAND = "^\\{.*\\}$".toRegex()



    private val _syncWindowState = MutableStateFlow<SyncWindowState>(SyncWindowState.Stopped)
    val syncWindowState = _syncWindowState.asStateFlow()

    internal fun onSyncWindowStarting(){
        _syncWindowState.value = SyncWindowState.Starting
    }
    internal fun onSyncWindowStarted(){
        _syncWindowState.value = SyncWindowState.Running
    }

    internal fun onSyncWindowStopping(){
        _syncWindowState.value = SyncWindowState.Stopping
    }
    internal fun onSyncWindowFinished(){
        _syncWindowState.value = SyncWindowState.Stopped
    }

    fun init(
        context: Context,
        channelId: String = DEFAULT_CHANNEL_ID,
        channelIcon: Int = R.drawable.ic_cloud,
        pushKey: PushKey? = null,
        configPathProvider: suspend () -> String?,
    ) {
        Log.d(TAG, "init")
        Upgrader(context).upgrade()
        this.configPathProvider = configPathProvider
        this.channelId = channelId
        this.channelIcon = channelIcon
        this.pushKey = pushKey
        nativeLibDir = context.applicationInfo.nativeLibraryDir
    }

    suspend fun start(context: Context?,
                      skipSync: Boolean = true,
                      timeoutMillis : Long
    ): Boolean{
        return withContext(scope.coroutineContext) {
            Log.d(TAG, "start")
            return@withContext try {
                stop()

                if(context == null){
                    Log.e(TAG, "Error: Null context on SMS bridge start()")
                    return@withContext false
                }

                // Open database
                BridgeDatabase.getInstance(context)

                if(!context.hasPermissions){
                    Log.e(TAG, "Error: Permissions aren't setup for the SMS bridge")
                    return@withContext false
                }

                if(!getConfig().exists()){
                    Log.e(TAG, "Error: Bridge config isn't setup for the SMS bridge")
                    return@withContext false
                }

                val startResult = startProcess(context, skipSync, timeoutMillis)
                if(!startResult){
                    Log.e(TAG, "Timeout waiting for prestartup hook")
                    return@withContext false
                }

                if(process == null || !process.running){
                    Log.e(TAG, "failed to start SMS bridge")
                    return@withContext false
                }

                Log.d(TAG, "SMS bridge was successfully started")
                true
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Error")
                false
            }
        }
    }

    fun stop() {
       killProcess()
    }

    suspend fun startProcess(context: Context, skipSync: Boolean, timeoutMillis : Long) : Boolean {
            val config = getConfig()
            val cache = getCacheDir(context)

            if(!config.exists()){
                Log.d(TAG, "imautrix-imessage config or cache path are missing")
                return false
            }

            if (!process.running) {
                process?.kill()
                Log.d(TAG, "Starting mautrix-imessage")
                process = ProcessBuilder()
                    .env(
                        "LD_LIBRARY_PATH" to nativeLibDir,
                        "TMPDIR" to cache,
                    )
                    .directory(nativeLibDir.toFile())
                    .command("./libmautrix.so", "-c", config)
                    .start()
                    .also {
                        if (it.running) {
                            Log.d(TAG, "mautrix-imessage is running")
                        }
                    }
                commandProcessor = StartStopCommandProcessor(
                    context,
                    pushKey,
                    commandsReceived = commandsReceived,
                    coroutineContext = scope.coroutineContext
                )
                errorHandling?.cancel()
                errorHandling = scope.launch {
                    forEachError {
                        Log.e("Mautrix-iMessage", it)
                    }
                }

                commandHandling?.cancel()
                commandHandling = scope.launch {
                    forEachCommand {
                        input ->
                        if (COMMAND.matches(input)) {
                            _commandsReceived.tryEmit(commandProcessor.inputToCommand(input))
                        } else {
                            Log.d("Mautrix-iMessage", input)
                        }
                    }
                }
                val startupResult = commandProcessor.awaitForPreStartupSync(skipSync,timeoutMillis)
                if(!startupResult){
                    Log.e(TAG, "Timeout starting the bridge")
                }else{
                    Log.d(TAG, "Pre startup sync hook received -> we can go on...")
                }
                return startupResult
            }else{
                Log.d(TAG, "Nothing to do: bridge is already running"
                        + "cache:$cache config: $config")
                return true
            }
    }

    fun enableSMSBridge(context: Context) {
        WorkManager(context).enableSMSBridge()
    }

    fun disableSMSBridge(context: Context) {
        WorkManager(context).disableSMSBridge()
    }


    @SuppressLint("ApplySharedPref")
    suspend fun storeNewChatThreadIdToBridge(context: Context, threadId: Long){
        return withContext(Dispatchers.IO) {
            val smsSharedPrefs = context.getSharedPreferences(
                SMS_SHARED_PREFS,
                Context.MODE_PRIVATE
            )

            val editor = smsSharedPrefs.edit()
            editor.putLong(NEW_CHAT_THREAD_ID_KEY, threadId)
            editor.commit()
        }
    }

    internal fun getNewChatThreadIdToBridge(context: Context) : Long{
        val smsSharedPrefs = context.getSharedPreferences(SMS_SHARED_PREFS,
            Context.MODE_PRIVATE)
        return smsSharedPrefs.getLong(NEW_CHAT_THREAD_ID_KEY, -1)
    }


    @SuppressLint("ApplySharedPref")
    internal suspend fun storeBackfillingState(context: Context, backfillComplete: Boolean){
        return withContext(Dispatchers.IO) {
            val smsSharedPrefs = context.getSharedPreferences(
                SMS_SHARED_PREFS,
                Context.MODE_PRIVATE
            )
            val editor = smsSharedPrefs.edit()
            editor.putBoolean(BACKFILLING_PREF_KEY, backfillComplete)
            editor.commit()
        }
    }

    fun getBackfillingState(context: Context) : Boolean{
        val smsSharedPrefs = context.getSharedPreferences(SMS_SHARED_PREFS,
            Context.MODE_PRIVATE)
        return smsSharedPrefs.getBoolean(BACKFILLING_PREF_KEY, false)
    }

    private suspend fun getConfig(): String? =
        configPath ?: configPathProvider?.invoke()?.takeIf { it.exists() }?.also { configPath = it }

    fun killProcess() {
        if(process!=null) {
            process?.kill()
        }else{
            Log.d(TAG, "No process to kill")
        }
    }

    private fun Process.kill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Log.d(TAG, "destroyForcibly")
                destroyForcibly()
            } catch (e: InterruptedException) {
                Log.e(TAG, e)
            }
        } else {
            Log.d(TAG, "Calling destroy")
            destroy()
            Log.d(TAG, "Called destroy")
        }
    }

    internal fun forEachError(action: (String) -> Unit) =
        process?.errorStream?.forEach(action) ?: Log.e(TAG, "forEachError failed")

    internal fun forEachCommand(action: (String) -> Unit) =
        process?.inputStream?.forEach(action) ?: Log.e(TAG, "forEachCommand failed")


    internal fun buildChatCommand(chat: Chat) : Command {
        return Command(
            "chat",
            data = chat,
            requestId.addAndGet(1)
        )
    }

    internal fun buildMessageCommand(message: Message) : Command {
        return Command(
            "message",
            data = message,
            requestId.addAndGet(1)
        )
    }

    internal fun buildReadReceiptCommand(readReceipt: ReadReceipt) : Command {
        return Command(
            "read_receipt",
            data = readReceipt,
            requestId.addAndGet(1)
        )
    }

    internal fun buildSendMessageStatusCommand(sendMessageStatus: SendMessageStatus) : Command {
        return Command(
            "send_message_status",
            data = sendMessageStatus,
            requestId.addAndGet(1)
        )
    }

    internal fun buildContactCommand(contact: Contact) : Command {
        return Command(
            "contact",
            data = contact,
            requestId.addAndGet(1)
        )
    }

    internal fun send(id: Int, error: Error) = send(Command("error", error, id))

    val running: Boolean
        get() = process?.running == true

    internal fun send(command: Command) = scope.launch(outgoing) {
        process
            ?.outputStream
            ?.writer()
            ?.apply {
                try {
                    val json = gson.toJson(command)
                    Log.d(TAG, "send${command.requestId}: $command")
                    append("$json\n")
                    flush()
                } catch (e: Exception) {
                    Log.e(TAG, e)
                }
            }
            ?: Log.e(TAG, "failed to send: $command")
    }

    internal fun confirmMessageDeliveryAndStoreMessage(context: Context,
                                                       command: Command, bridgedMessage: BridgedMessage)
    = scope.launch(outgoing) {
        process
            ?.outputStream
            ?.writer()
            ?.apply {
                try {
                    val json = gson.toJson(command)
                    Log.d(TAG, "send${command.requestId}: $command")
                    append("$json\n")
                    flush()
                    Log.d(TAG, "DB storing bridged message:" +
                                " chat_guid:${bridgedMessage.chat_guid} " +
                                " message_id:${bridgedMessage.message_id} " +
                                " isMms:${bridgedMessage.is_mms}"
                    )
                    BridgeDatabase.getInstance(context)
                        .bridgedMessageDao().insert(bridgedMessage)
                } catch (e: Exception) {
                    Log.e(TAG, e)
                }
            }
            ?: Log.e(TAG, "failed to send: $command")
    }

    internal fun forwardMessageToBridge(postMeThisMessage: BridgeThisSmsOrMms)
            = scope.launch {
        Log.d(TAG, "forwardMessageToBridge before posting SMS/MMS message")

        _commandsReceived.emit(
                Command(
                    "bridge_this_message",
                    data = postMeThisMessage
                )
            )
    }

    internal fun forwardSendResponseToBridge(bridgeSendResponse: BridgeSendResponse)
            = scope.launch {
        Log.d(TAG, "forwardSendResponseToBridge -> message successfully sent commandId#: $bridgeSendResponse.commandId")

        _commandsReceived.emit(
            Command(
                "bridge_send_response",
                data = bridgeSendResponse,
                id = bridgeSendResponse.commandId
            )
        )
    }

    internal fun forwardSendErrorToBridge(commandId: Int, bridgeSendError: BridgeSendError)
            = scope.launch {
        Log.w(TAG, "forwardSendErrorToBridge -> error sending message commandId#:$commandId")

        _commandsReceived.emit(
            Command(
                "bridge_send_response_error",
                data = bridgeSendError,
                id = commandId
            )
        )
    }

    suspend fun forwardChatToBridge(threadId: Long) {
        Log.d(TAG, "Forwarding chat to bridge: ThreadId: $threadId")
        commandProcessor.bridgeChatWithThreadId(threadId)
    }

    suspend fun forwardReadReceiptToBridge(readReceiptToBeBridged: BridgeReadReceipt) {
        Log.d(TAG, "Forwarding ReadReceipt to bridge: " +
                "${readReceiptToBeBridged.readReceipt}")
        commandProcessor.sendReadReceiptCommandAndAwaitForResponse(
            readReceiptToBeBridged.readReceipt, 5000)
    }

    suspend fun addPendingReadReceipt(context: Context, readReceiptToBeBridged: BridgeReadReceipt) {
        withContext(Dispatchers.IO) {
            val databaseInstance = BridgeDatabase.getInstance(context)

            Log.d(
                TAG, "Adding pending readReceipt to be bridged in next sync window: " +
                        "${readReceiptToBeBridged.readReceipt}"
            )
            databaseInstance.pendingReadReceiptDao().insert(
                PendingReadReceipt(
                    readReceiptToBeBridged.readReceipt.chat_guid,
                    readReceiptToBeBridged.readReceipt.read_up_to,
                    readReceiptToBeBridged.readReceipt.read_at.toLong(),
                )
            )
            Log.d(
                TAG, "Pending read receipt added: " +
                        "${readReceiptToBeBridged.readReceipt}"
            )
        }
    }

    fun markMessageAsRead(message_guid: String, context: Context){
        //val databaseInstance = BridgeDatabase.getInstance(context)
        //val inboxPreviewCacheDao = databaseInstance.inboxPreviewCacheDao()

        //if(lastMessage != null){
            val inboxPreviewProvider = InboxPreviewProviderLocator.getInstance(context)
            Log.v(TAG, "marking message_guid as read: $message_guid")
            inboxPreviewProvider.markMessagesInThreadAsRead(message_guid)
                //}
    }

    fun getDBFile(context: Context): File{
        return File(context.filesDir, "mautrix-imessage.db")
    }

    fun getLogFile(context: Context): File{
        return File(context.cacheDir, "mautrix-imessage.log")
    }

    private fun getCacheDir(context: Context): String{
        return context.cacheDirPath(MAUTRIX_CACHE_SUBDIR)
    }

    private fun getMMSCacheDir(context: Context): String{
        return context.mmsDir.absolutePath
    }

    // Deletes all mautrix-imessage related data
    private fun deleteBridgeFiles(context:Context){
        getDBFile(context).delete()
        getLogFile(context).delete()
        getCacheDir(context).delete()
        getMMSCacheDir(context).delete()
    }

    internal suspend fun clearBridgeData(context:Context, deleteBridgeDB: Boolean = false){
        withContext(scope.coroutineContext){
            storeBackfillingState(context,false)
            stop()
            // Delete bridge files after clearing the bridge
            if(deleteBridgeDB){
                deleteBridgeFiles(context)
            }
            configPath = null
            configPathProvider = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(context:Context, notificationChannelId: String) =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(
                    notificationChannelId,
                    context.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    enableLights(false)
                    enableVibration(false)
                    setBypassDnd(false)
                    setShowBadge(false)
                }
            )

    fun buildNotification(context:Context,
                          notificationChannelId: String,
                          contentText: String): Notification {
        return NotificationCompat.Builder(context, notificationChannelId)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(channelIcon).build()
    }

    fun buildErrorNotification(context: Context, title: String, contentText: String) : Notification{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context, DEFAULT_CHANNEL_ID)
        }
        return NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(channelIcon).build()
    }

    companion object {
        const val VERSION = 2
        const val ONGOING_NOTIFICATION_ID = 10681
        const val DEFAULT_STARTUP_TIMEOUT_MILLIS = 30000L
        private const val TAG = "StartStopBridge"
        const val DEFAULT_CHANNEL_ID = "sms_bridge"

        private const val SMS_SHARED_PREFS = "com.beeper.sms.prefs"
        private const val BACKFILLING_PREF_KEY = "isBackfillComplete"
        private const val NEW_CHAT_THREAD_ID_KEY = "newChatThreadId"
        private const val MAUTRIX_CACHE_SUBDIR = "mautrix"

        private val gson = newGson()
        val INSTANCE = StartStopBridge()

        val Process?.running: Boolean
            get() = try {
                this?.exitValue()
                    ?.let { Log.w(TAG, "exited: $it") }
                    ?: Log.d(TAG, "no process")
                false
            } catch (e: IllegalThreadStateException) {
                true
            }

        private fun InputStream.forEach(action: (String) -> Unit) {
            Log.d(TAG, "$this forEach")
            reader().buffered().let { reader ->
                try {
                    for (element in reader.lineSequence()) {
                        action(element)
                    }
                } catch (e: Exception) {
                    if(e is InterruptedIOException){
                        Log.d(TAG, "InputStream was interrupted")
                    }else {
                        Log.e(TAG, e)
                    }
                } finally {
                    try {
                        reader.close()
                    } catch (ignored: Throwable) {
                    }
                }
            }
            Log.d(TAG, "$this closed")
        }

        private fun String.toFile(): File? = File(this).takeIf { it.exists() }

        private fun String.delete() = toFile()?.deleteRecursively()?.let {
            if (it) Log.d(TAG, "Deleted $this") else Log.e(TAG, "Failed to delete $this")
        }

        private fun String?.exists(): Boolean = this?.let { File(it) }?.exists() == true

        private val Command.requestId: String
            get() = if (id != null && command != "response") " #$id" else ""
    }
}