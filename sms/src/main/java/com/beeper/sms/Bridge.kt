package com.beeper.sms

import android.content.Context
import android.os.Build
import com.beeper.sms.BridgeService.Companion.startBridge
import com.beeper.sms.BridgeService.Companion.stopBridge
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.commands.outgoing.PushKey
import com.beeper.sms.extensions.cacheDir
import com.beeper.sms.extensions.env
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.extensions.mmsCache
import com.beeper.sms.helpers.newGson
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.suspendCoroutine

class Bridge private constructor() {
    private lateinit var nativeLibDir: String
    private lateinit var channelId: String
    private var configPathProvider: (suspend () -> String?)? = null
    private var configPath: String? = null
    private var cacheDir: String? = null
    private var channelIcon: Int? = null
    private var pushKey: PushKey? = null
    private var process: Process? = null
        get() = field?.takeIf { it.running }
    private val outgoing = newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val responseFlow = MutableSharedFlow<Pair<Int, JsonElement>>()
    private val requestId = AtomicInteger(0)

    fun init(
        context: Context,
        channelId: String = DEFAULT_CHANNEL_ID,
        channelIcon: Int? = null,
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
        if (File(context.mmsCache).deleteRecursively()) {
            Log.d(TAG, "Deleted MMS cache")
        } else {
            Log.e(TAG, "Failed to delete MMS cache")
        }
        SmsObserver(context).registerObserver()
    }

    fun start(context: Context?) = scope.launch {
        Log.d(TAG, "start")
        try {
            if (context?.hasPermissions == true &&
                getConfig().exists() &&
                process?.running != true
            ) {
                context.startBridge(channelId, channelIcon, pushKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Error")
        }
    }

    fun stop(context: Context) =
        context
            .stopBridge()
            .apply { Log.d(TAG, "stop success=$this") }

    fun signOut(context: Context) {
        Log.d(TAG, "Stopping bridge")
        stop(context)
        configPath = null
        configPathProvider = null
    }

    fun ping() {
        send(Command("ping_server", null))
    }

    fun startProcess(): Process? {
        val config = configPath ?: return null
        val cache = cacheDir ?: return null
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
                        Log.d(TAG, "Started")
                    }
                }
        }
        return process
    }

    private suspend fun getConfig(): String? =
        configPath ?: configPathProvider?.invoke()?.takeIf { it.exists() }?.also { configPath = it }

    fun killProcess() {
        process?.kill() ?: Log.d(TAG, "No process to kill")
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

    internal fun publishResponse(id: Int, dataTree: JsonElement) = scope.launch {
        responseFlow.emit(Pair(id, dataTree))
    }

    internal suspend fun await(command: Command): JsonElement = suspendCoroutine { continuation ->
        scope.launch {
            command.id = requestId.addAndGet(1)
            responseFlow
                .onSubscription { send(command) }
                .first { it.first == command.id }
                .let { continuation.resumeWith(Result.success(it.second)) }
        }
    }

    internal fun send(id: Int, error: Error) = send(Command("error", error, id))

    internal val running: Boolean
        get() = process?.running == true

    internal fun send(command: Command) = scope.launch(outgoing) {
        process
            ?.outputStream
            ?.writer()
            ?.apply {
                try {
                    Log.d(TAG, "send${command.requestId}: $command")
                    append("${gson.toJson(command)}\n")
                    flush()
                } catch (e: Exception) {
                    Log.e(TAG, e)
                }
            }
            ?: Log.e(TAG, "failed to send: $command")
    }

    companion object {
        private const val TAG = "Bridge"
        private const val DEFAULT_CHANNEL_ID = "sms_bridge_service"
        private val gson = newGson()
        val INSTANCE = Bridge()

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
                    Log.e(TAG, e)
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