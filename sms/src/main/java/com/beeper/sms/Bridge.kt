package com.beeper.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.Message
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.*
import java.math.BigDecimal
import java.util.concurrent.Executors.newSingleThreadExecutor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class Bridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandProcessor: CommandProcessor,
    private val smsProvider: SmsProvider,
) {
    private lateinit var stdin: BufferedWriter
    private val outgoing = newSingleThreadExecutor().asCoroutineDispatcher()
    private val gson =
        GsonBuilder().registerTypeAdapter(DOUBLE_SERIALIZER_TYPE, DOUBLE_SERIALIZER).create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(configPath: String): Boolean = try {
        val process = ProcessBuilder()
            .apply { environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir }
            .directory(File(context.applicationInfo.nativeLibraryDir))
            .command("./libmautrix.so", "-c", configPath)
            .start()

        stdin = BufferedWriter(OutputStreamWriter(process.outputStream))
        val stdout = InputStreamReader(process.inputStream)
        val stderr = InputStreamReader(process.errorStream)

        with(scope) {
            launch {
                stderr.forEachLine {
                    it.takeIf { it.isNotBlank() }?.let { err ->
                        Log.e(TAG, err)
                        exitProcess(1)
                    }
                }
            }

            launch {
                stdout.forEachLine {
                    if (it.startsWith("{") && it.endsWith("}")) {
                        Log.d(TAG, "receive: $it")
                        commandProcessor.handle(it)?.let { response ->
                            send(response)
                        }
                    } else {
                        Log.d(TAG, it)
                    }
                }
            }
        }
        try {
            process.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, e.message ?: "Error")
        false
    }

    fun send(uri: Uri) = scope.launch {
        val message = smsProvider.getMessage(uri) ?: return@launch
        send(message)
    }

    private fun send(message: Message) = send(Command("message", message))

    fun send(command: Command) = scope.launch(outgoing) {
        val json = gson.toJson(command)
        Log.d(TAG, "send: $json")
        stdin.append(json).append('\n').flush()
    }

    companion object {
        private const val TAG = "Bridge"
        private val DOUBLE_SERIALIZER_TYPE = object : TypeToken<Double>() {}.type
        private val DOUBLE_SERIALIZER =
            JsonSerializer<Double> { src, _, _ -> JsonPrimitive(BigDecimal.valueOf(src)) }
    }
}