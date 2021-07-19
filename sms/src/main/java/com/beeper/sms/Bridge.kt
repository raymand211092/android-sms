package com.beeper.sms

import android.content.Context
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
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.util.concurrent.Executors.newSingleThreadExecutor

class Bridge private constructor() {
    private lateinit var stdin: BufferedWriter
    lateinit var stdout: InputStreamReader
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
        val process = ProcessBuilder()
            .env(
                "LD_LIBRARY_PATH" to context.applicationInfo.nativeLibraryDir,
                "TMPDIR" to context.cacheDir("mautrix"),
            )
            .directory(File(context.applicationInfo.nativeLibraryDir))
            .command("./libmautrix.so", "-c", configPath)
            .start()

        stdin = BufferedWriter(OutputStreamWriter(process.outputStream))
        stdout = InputStreamReader(process.inputStream)
        val stderr = InputStreamReader(process.errorStream)

        with(scope) {
            launch {
                stderr.forEachLine {
                    it.takeIf { it.isNotBlank() }?.let { err ->
                        Log.e(TAG, err)
                    }
                }
            }
        }
        try {
            process.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            context.startBridge(channelId, channelIcon)
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, e.message ?: "Error")
        false
    }

    fun send(command: Command) = scope.launch(outgoing) {
        val json = gson.toJson(command)
        Log.d(TAG, "send: $json")
        stdin.append(json).append('\n').flush()
    }

    companion object {
        private const val TAG = "Bridge"
        private const val DEFAULT_CHANNEL_ID = "sms_bridge_service"
        private val DOUBLE_SERIALIZER_TYPE = object : TypeToken<Double>() {}.type
        private val DOUBLE_SERIALIZER =
            JsonSerializer<Double> { src, _, _ -> JsonPrimitive(BigDecimal.valueOf(src)) }
        val INSTANCE = Bridge()
    }
}