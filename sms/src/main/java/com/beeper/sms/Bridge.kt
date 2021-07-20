package com.beeper.sms

import android.content.Context
import android.os.Build
import android.util.Log
import com.beeper.sms.BridgeService.Companion.startBridge
import com.beeper.sms.commands.Command
import com.beeper.sms.extensions.cacheDir
import com.beeper.sms.extensions.env
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
    private lateinit var configPath: String
    private lateinit var nativeLibDir: String
    private lateinit var cacheDir: String
    private var process: Process? = null
    private val outgoing = newSingleThreadExecutor().asCoroutineDispatcher()
    private val gson =
        GsonBuilder().registerTypeAdapter(DOUBLE_SERIALIZER_TYPE, DOUBLE_SERIALIZER).create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(
        context: Context,
        configPath: String,
        channelId: String = DEFAULT_CHANNEL_ID,
        channelIcon: Int? = null,
    ): Boolean = try {
        this.configPath = configPath
        nativeLibDir = context.applicationInfo.nativeLibraryDir
        cacheDir = context.cacheDir("mautrix")
        if (getProcess()?.running == true) {
            context.startBridge(channelId, channelIcon)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, e.message ?: "Error")
        false
    }

    @Synchronized
    private fun getProcess(): Process? {
        if (process?.running != true) {
            Log.d(TAG, "Starting mautrix-imessage")
            process = ProcessBuilder()
                .env(
                    "LD_LIBRARY_PATH" to nativeLibDir,
                    "TMPDIR" to cacheDir,
                )
                .directory(File(nativeLibDir))
                .command("./libmautrix.so", "-c", configPath)
                .start()
        }
        return process
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process?.destroyForcibly()
        } else {
            process?.destroy()
        }
        process = null
    }

    fun forEachError(action: (String) -> Unit) =
        getProcess()?.errorStream?.forEach(action) ?: Log.e(TAG, "forEachError failed")

    fun forEachCommand(action: (String) -> Unit) =
        getProcess()?.inputStream?.forEach(action) ?: Log.e(TAG, "forEachCommand failed")

    fun send(command: Command) = scope.launch(outgoing) {
        getProcess()
            ?.outputStream
            ?.writer()
            ?.apply {
                Log.d(TAG, "send: $command")
                append("${gson.toJson(command)}\n")
                flush()
            }
            ?: Log.e(TAG, "failed to send: $command")
    }

    companion object {
        private const val TAG = "Bridge"
        private const val DEFAULT_CHANNEL_ID = "sms_bridge_service"
        private val DOUBLE_SERIALIZER_TYPE = object : TypeToken<Double>() {}.type
        private val DOUBLE_SERIALIZER =
            JsonSerializer<Double> { src, _, _ -> JsonPrimitive(BigDecimal.valueOf(src)) }
        val INSTANCE = Bridge()

        val Process.running: Boolean
            get() = try {
                exitValue()
                false
            } catch (e: IllegalThreadStateException) {
                true
            }

        fun InputStream.forEach(action: (String) -> Unit) {
            Log.d(TAG, "$this forEach")
            reader().forEachLine(action)
            Log.d(TAG, "$this closed")
        }
    }
}