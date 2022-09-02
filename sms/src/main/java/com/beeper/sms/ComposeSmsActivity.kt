package com.beeper.sms

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.Chat
import com.beeper.sms.extensions.getText
import com.beeper.sms.extensions.getThread
import com.beeper.sms.extensions.recipients
import com.beeper.sms.provider.ContactProvider

class ComposeSmsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != Intent.ACTION_SENDTO && intent.action != Intent.ACTION_SEND) {
            return
        }
        handleIntent()
        finish()
    }

    private fun handleIntent() {
        val recipients = intent.recipients ?: return
        val text = intent.getText(Intent.EXTRA_TEXT)
        if (text == null) {
            /*val room =
                ContactProvider(this)
                    .getRecipients(recipients)
                    .map { contact -> contact.first.nickname }
                    .joinToString()
            Bridge.INSTANCE.send(
                Command(
                    "chat",
                    Chat("", room, recipients)
                )
            )*/
        } else {
            SmsMmsSender(this).sendMessage(
                text,
                recipients,
                getThread(recipients),
                subject = intent.getText(Intent.EXTRA_SUBJECT)
            )
        }
    }
}
