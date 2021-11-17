package com.beeper.sms

import android.content.Context
import android.os.Build
import com.beeper.sms.BridgeService.Companion.startBridge
import com.beeper.sms.BridgeService.Companion.stopBridge
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.extensions.cacheDir
import com.beeper.sms.extensions.env
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.work.BackfillSentMMS
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.util.concurrent.Executors.newSingleThreadExecutor

class Bridge private constructor() {
    private lateinit var nativeLibDir: String
    private lateinit var channelId: String
    private var configPathProvider: (suspend () -> String?)? = null
    private var configPath: String? = null
    private var cacheDir: String? = null
    private var channelIcon: Int? = null
    private var process: Process? = null
    private val outgoing = newSingleThreadExecutor().asCoroutineDispatcher()
    private val gson =
        GsonBuilder().registerTypeAdapter(DOUBLE_SERIALIZER_TYPE, DOUBLE_SERIALIZER).create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(
        context: Context,
        channelId: String = DEFAULT_CHANNEL_ID,
        channelIcon: Int? = null,
        configPathProvider: suspend () -> String?,
    ) {
        Log.d(TAG, "init")
        Upgrader(context).upgrade()
        this.configPathProvider = configPathProvider
        this.channelId = channelId
        this.channelIcon = channelIcon
        nativeLibDir = context.applicationInfo.nativeLibraryDir
        cacheDir = context.cacheDir("mautrix")
        SmsObserver(context).registerObserver()
    }

    fun start(context: Context?) = scope.launch {
        Log.d(TAG, "start")
        try {
            if (context?.hasPermissions == true &&
                getConfig().exists() &&
                getProcess()?.running == true
            ) {
                context.startBridge(channelId, channelIcon)
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

    @Synchronized
    private fun getProcess(): Process? {
        val config = configPath ?: return null
        val cache = cacheDir ?: return null
        if (process?.running != true) {
            Log.d(TAG, "Starting mautrix-imessage")
            process = ProcessBuilder()
                .env(
                    "LD_LIBRARY_PATH" to nativeLibDir,
                    "TMPDIR" to cache,
                )
                .directory(nativeLibDir.toFile())
                .command("./libmautrix.so", "-c", config)
                .start()
        }
        return process
    }

    private suspend fun getConfig(): String? =
        configPath ?: configPathProvider?.invoke()?.takeIf { it.exists() }?.also { configPath = it }

    fun killProcess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process?.destroyForcibly()
        } else {
            process?.destroy()
        }
        process = null
    }

    internal fun forEachError(action: (String) -> Unit) =
        getProcess()?.errorStream?.forEach(action) ?: Log.e(TAG, "forEachError failed")

    internal fun forEachCommand(action: (String) -> Unit) =
        getProcess()?.inputStream?.forEach(action) ?: Log.e(TAG, "forEachCommand failed")

    internal fun send(id: Int, error: Error) = send(Command("error", error, id))

    internal fun send(command: Command) = send(command as Any)

    internal val running: Boolean
        get() = getProcess()?.running == true

    private fun send(any: Any) = scope.launch(outgoing) {
        getProcess()
            ?.outputStream
            ?.writer()
            ?.apply {
                Log.d(TAG, "send: $any")
                append("${gson.toJson(any)}\n")
                flush()
            }
            ?: Log.e(TAG, "failed to send: $any")
    }

    companion object {
        private const val TAG = "Bridge"
        private const val DEFAULT_CHANNEL_ID = "sms_bridge_service"
        private val DOUBLE_SERIALIZER_TYPE = object : TypeToken<Double>() {}.type
        private val DOUBLE_SERIALIZER =
            JsonSerializer<Double> { src, _, _ -> JsonPrimitive(BigDecimal.valueOf(src)) }
        val INSTANCE = Bridge()

        private val Process.running: Boolean
            get() = try {
                exitValue()
                false
            } catch (e: IllegalThreadStateException) {
                true
            }

        private fun InputStream.forEach(action: (String) -> Unit) {
            Log.d(TAG, "$this forEach")
            reader().forEachLine(action)
            Log.d(TAG, "$this closed")
        }

        private fun String.toFile(): File? = File(this).takeIf { it.exists() }

        private fun String?.exists(): Boolean = this?.let { File(it) }?.exists() == true
    }
}