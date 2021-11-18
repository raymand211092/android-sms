package com.beeper.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.PushKey
import com.beeper.sms.extensions.hasPermissions
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InterruptedIOException

class BridgeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var channelId: String
    private var pushKey: PushKey? = null
    private var channelIcon: Int = 0
    private var errorHandling: Job? = null
    private var commandHandling: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "starting service")
        channelId = intent?.getStringExtra(CHANNEL_ID)
            ?: throw RuntimeException("Missing channel_id")
        pushKey = intent.getSerializableExtra(PUSH_KEY) as PushKey?
        channelIcon = intent.getIntExtra(CHANNEL_ICON, DEFAULT_CHANNEL_ICON)
        startForeground(
            ONGOING_NOTIFICATION_ID,
            NotificationCompat.Builder(this, channelId)
                .setSound(null)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(channelIcon)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_body))
                .build()
        )
        if (!hasPermissions) {
            Log.e(TAG, "stopping service: not default SMS app")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val commandProcessor = CommandProcessor(applicationContext)
        errorHandling?.cancel()
        errorHandling = restartOnInterrupt {
            Bridge.INSTANCE.forEachError {
                Log.e(TAG, it)
            }
        }
        commandHandling?.cancel()
        commandHandling = restartOnInterrupt {
            pushKey?.let { Bridge.INSTANCE.send(Command("push_key", it)) }
            Bridge.INSTANCE.forEachCommand {
                if (COMMAND.matches(it)) {
                    commandProcessor.handle(it)
                } else {
                    Log.d(TAG, it)
                }
            }
        }
        return if (Bridge.INSTANCE.running) {
            START_REDELIVER_INTENT
        } else {
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        errorHandling?.cancel()
        commandHandling?.cancel()
        Bridge.INSTANCE.killProcess()
    }

    private fun restartOnInterrupt(block: () -> Unit) = scope.launch {
        try {
            block()
        } catch (e: InterruptedIOException) {
            Log.e(TAG, e)
            yield()
            startBridge(channelId, channelIcon, pushKey)
        } catch (e: IOException) {
            Log.e(TAG, e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BridgeService"
        private const val ONGOING_NOTIFICATION_ID = 10681
        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_ICON = "channel_icon"
        private const val PUSH_KEY = "push_key"
        private val DEFAULT_CHANNEL_ICON = R.drawable.ic_status_bar_beeper
        @Suppress("RegExpRedundantEscape")
        private val COMMAND = "^\\{.*\\}$".toRegex()

        internal fun Context.startBridge(
            channelId: String,
            channelIcon: Int? = null,
            pushKey: PushKey? = null,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel(channelId)
            }
            ContextCompat.startForegroundService(
                this,
                bridgeIntent
                    .putExtra(CHANNEL_ID, channelId)
                    .putExtra(CHANNEL_ICON, channelIcon)
                    .putExtra(PUSH_KEY, pushKey)
            )
        }

        internal fun Context.stopBridge() = stopService(bridgeIntent)

        private val Context.bridgeIntent: Intent
            get() = Intent(this, BridgeService::class.java)

        @RequiresApi(Build.VERSION_CODES.O)
        private fun Context.createNotificationChannel(channelId: String) =
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        getString(R.string.notification_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        enableLights(false)
                        enableVibration(false)
                        setBypassDnd(false)
                        setShowBadge(false)
                    }
                )
    }
}