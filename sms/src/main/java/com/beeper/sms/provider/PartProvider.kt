package com.beeper.sms.provider

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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

            val fileExtension = mimetype.split("/").getOrNull(1)

            val data = it.getString("_data")
            when (mimetype) {
                "text/plain" -> {
                    Part(text = data?.let { getMmsText(partId) } ?: it.getString(TEXT))
                }
                "application/smil" -> {
                    Log.v(TAG, "Ignoring $mimetype: ${it.getString(TEXT)}")
                    null
                }
                else -> {
                    val folder = File(context.mmsCache)
                    if (!folder.exists()) {
                        folder.mkdir();
                    }
                    val fileNameSuffix = UUID.randomUUID().toString()
                    val fileName = if (fileExtension != null) {
                        fileNameSuffix + ".${fileExtension}"
                    } else {
                        fileNameSuffix
                    }
                    val file = File(context.mmsCache, fileName)
                    if (!file.exists()) {
                        file.createNewFile()
                    }

                    val uri = "$URI_PART/$partId".toUri()
                    cr.openInputStream(uri)?.writeTo(file)

                    val mediaThumbnailSize: Pair<Int, Int>? =
                        extractMediaDimensions(mimetype, file)

                    Part(
                        attachment = Attachment(
                            mime_type = mimetype,
                            file_name = it.getString(NAME) ?: file.name,
                            path_on_disk = file.absolutePath,
                            mediaThumbnailSize?.first,
                            mediaThumbnailSize?.second
                        )
                    )
                }
            }
        }

    private fun extractMediaDimensions(
        mimetype: String,
        file: File
    ): Pair<Int, Int>? {
        try {
            if (mimetype.startsWith("image")) {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(
                        cr.openInputStream(file.toUri()),
                        null,
                        options
                    )
                    return Pair(options.outHeight, options.outWidth)
            } else {
                if (mimetype.startsWith("video")) {
                        val mediaMetadataRetriever = MediaMetadataRetriever()
                        val fd =
                            cr.openFileDescriptor(file.toUri(), "r")?.fileDescriptor
                        mediaMetadataRetriever.setDataSource(fd)
                        val heightMetadata = mediaMetadataRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                        )
                        val widthMetadata = mediaMetadataRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                        )
                        if (heightMetadata != null && widthMetadata != null) {
                            return Pair(
                                Integer.valueOf(
                                    heightMetadata
                                ),
                                Integer.valueOf(
                                    widthMetadata
                                ),
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            // Ignore extracting metadata if something fails
        }
        return null
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