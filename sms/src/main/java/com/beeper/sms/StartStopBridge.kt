package com.beeper.sms

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.beeper.sms.StartStopBridge.Companion.requestId
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Chat
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.PushKey
import com.beeper.sms.database.BridgedEntitiesDatabase
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.extensions.cacheDir
import com.beeper.sms.extensions.env
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.extensions.mmsCache
import com.beeper.sms.helpers.newGson
import com.beeper.sms.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StartStopBridge private constructor() {
    lateinit var commandProcessor : StartStopCommandProcessor
    lateinit var database : BridgedEntitiesDatabase
        private set
    private lateinit var nativeLibDir: String
    private var channelId: String = DEFAULT_CHANNEL_ID

    private var configPathProvider: (suspend () -> String?)? = null
    private var configPath: String? = null
    private var cacheDir: String? = null
    private var channelIcon: Int = R.drawable.ic_cloud
    private var pushKey: PushKey? = null
    private var process: Process? = null
        get() = field?.takeIf { it.running }
    private val outgoing = newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // TODO: Adjust shared flow capacity
    private val _commandsReceived = MutableSharedFlow<Command>(
            replay = 0,
        extraBufferCapacity = 100,
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
        cacheDir = context.cacheDir("mautrix")
        File(context.mmsCache).deleteRecursively()
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
                database = Room.databaseBuilder(
                    context,
                    BridgedEntitiesDatabase::class.java, "sms-bridged-entities"
                ).build()

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
                // Open database
                database = Room.databaseBuilder(
                    context,
                    BridgedEntitiesDatabase::class.java, "sms-bridged-entities"
                ).build()
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
            val cache = cacheDir

            if(!config.exists() || cache==null){
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
        if(this::database.isInitialized) {
            if(database.isOpen) {
                database.close()
            }
        }
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
                val success = destroyForcibly().waitFor(10, TimeUnit.SECONDS)
                Log.d(TAG, "destroyForcibly success=$success")
                if (success) {
                    process = null
                }
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
                    Log.d(TAG, "send${command.requestId}: $command --- json: $json")
                    append("$json\n")
                    flush()
                } catch (e: Exception) {
                    Log.e(TAG, e)
                }
            }
            ?: Log.e(TAG, "failed to send: $command")
    }

    internal fun confirmMessageDeliveryAndStoreMessage(command: Command, bridgedMessage: BridgedMessage)
    = scope.launch(outgoing) {
        process
            ?.outputStream
            ?.writer()
            ?.apply {
                try {
                    val json = gson.toJson(command)
                    Log.d(TAG, "send${command.requestId}: $command --- json: $json")
                    append("$json\n")
                    flush()
                    Log.d(TAG, "DB storing bridged message:" +
                                " chat_guid:${bridgedMessage.chat_guid} " +
                                " message_id:${bridgedMessage.message_id} " +
                                " isMms:${bridgedMessage.is_mms}"
                    )
                    database.bridgedMessageDao().insert(bridgedMessage)
                } catch (e: Exception) {
                    Log.e(TAG, e)
                }
            }
            ?: Log.e(TAG, "failed to send: $command")
    }

    internal fun forwardMessageToBridge(postMeThisMessage: BridgeThisSmsOrMms)
            = scope.launch(outgoing) {
            _commandsReceived.tryEmit(
                Command(
                    "bridge_this_message",
                    data = postMeThisMessage
                )
            )
    }

    suspend fun forwardChatToBridge(threadId: Long) {
        Log.d(TAG, "Forwarding chat to bridge: ThreadId: $threadId")
        commandProcessor.bridgeChatWithThreadId(threadId)
    }



    internal suspend fun clearBridgeData(context:Context){
        withContext(scope.coroutineContext){
            storeBackfillingState(context,false)
            stop()
            cacheDir?.delete()
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
            createNotificationChannel(context, BRIDGE_ERROR_CHANNEL_ID)
        }
        return NotificationCompat.Builder(context, BRIDGE_ERROR_CHANNEL_ID)
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
        private const val BRIDGE_ERROR_CHANNEL_ID = "sms_bridge_error"

        private const val SMS_SHARED_PREFS = "com.beeper.sms.prefs"
        private const val BACKFILLING_PREF_KEY = "isBackfillComplete"
        private const val NEW_CHAT_THREAD_ID_KEY = "newChatThreadId"
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