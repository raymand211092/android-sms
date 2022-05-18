package com.beeper.sms.app.ui.components

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.PrecomputedTextCompat
import androidx.core.widget.TextViewCompat
import com.beeper.sms.app.ui.components.DimensionUtils.px
import com.beeper.sms.app.ui.components.TextUtils.hasClickableSpan


@Composable
fun TextMessage(
    text: CharSequence,
    textColor: Int,
    linkTextColor: Int,
    isClickable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(factory = {
        val messageView = AppCompatTextView(it)
        messageView.lineHeight = 18.px
        messageView.textSize = 15F
        messageView.setLineSpacing(2F, 1F)
        messageView.setTextColor(textColor)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START
        }
        messageView.layoutParams = params

        messageView.isClickable = isClickable
        messageView.setTextColor(textColor)
        messageView.setLinkTextColor(linkTextColor)
        messageView.setOnClickListener {
            onClick()
        }
        messageView.onLongClickIgnoringLinks {
            onLongClick()
            true
        }
        messageView.setTextWithEmojiSupport(text)
        messageView
    }, update = {
        it.setTextWithEmojiSupport(text)
    }, modifier = modifier
    )
}



fun TextView.onLongClickIgnoringLinks(listener: View.OnLongClickListener?) {
    if (listener == null) {
        setOnLongClickListener(null)
    } else {
        setOnLongClickListener(object : View.OnLongClickListener {
            override fun onLongClick(v: View): Boolean {
                if (hasLongPressedLink()) {
                    return false
                }
                return listener.onLongClick(v)
            }

            /**
             * Infer that a Clickable span has been click by the presence of a selection
             */
            private fun hasLongPressedLink() = selectionStart != -1 || selectionEnd != -1
        })
    }
}

object TextUtils {
    val CharSequence?.hasClickableSpan: Boolean
        get() = this is Spanned && nextSpanTransition(0, length, ClickableSpan::class.java) < length
}


private fun AppCompatTextView.setTextWithEmojiSupport(message: CharSequence?) {
    // PrecomputedTextCompat.getTextFuture is removing underlines from URLSpans
    if (message != null && !message.hasClickableSpan) {
        val textFuture = PrecomputedTextCompat.getTextFuture(
            message,
            TextViewCompat.getTextMetricsParams(this),
            null
        )
        setTextFuture(textFuture)
    } else {
        setTextFuture(null)
        text = message
    }
}