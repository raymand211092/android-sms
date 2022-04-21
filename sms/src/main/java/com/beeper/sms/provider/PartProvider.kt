package com.beeper.sms.provider

import android.content.Context
import android.provider.Telephony
import android.provider.Telephony.Mms.Part.*
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.commands.outgoing.Message.Attachment
import com.beeper.sms.extensions.*
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*


class PartProvider constructor(private val context: Context) {
    private val cr = context.contentResolver

    fun getAttachment(message: Long): List<Part> =
        cr.map(URI_PART, "$MSG_ID = $message") {
            val partId = it.getLong(_ID)
            val mimetype = it.getString(CONTENT_TYPE) ?: ""
            val data = it.getString("_data")
            when (mimetype) {
                "text/plain" -> {
                    Part(text = data?.let { getMmsText(partId) } ?: it.getString(TEXT))
                }
                "application/smil" -> {
                    Log.d(TAG, "Ignoring $mimetype: ${it.getString(TEXT)}")
                    null
                }
                else -> {
                    val file = File(context.mmsCache, UUID.randomUUID().toString())
                    cr.openInputStream("$URI_PART/$partId".toUri())?.writeTo(file)
                    Part(
                        attachment = Attachment(
                            mime_type = mimetype,
                            file_name = it.getString(NAME) ?: UUID.randomUUID().toString(),
                            path_on_disk = file.absolutePath,
                        )
                    )
                }
            }
        }

    private fun getMmsText(id: Long) =
        cr.openInputStream("$URI_PART/$id".toUri())?.use {
            InputStreamReader(it, UTF_8).readLines().joinToString("")
        }

    data class Part(
        var text: String? = null,
        var attachment: Attachment? = null,
    )

    companion object {
        private const val TAG = "PartProvider"
        private val URI_PART = "${Telephony.Mms.CONTENT_URI}/part".toUri()
    }
}