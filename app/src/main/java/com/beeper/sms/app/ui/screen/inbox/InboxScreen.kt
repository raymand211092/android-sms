package com.beeper.sms.app.ui.screen.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import com.beeper.sms.app.ui.components.SMSAppBar
import com.beeper.sms.app.ui.components.external.SMSThreadsSection
import com.beeper.sms.app.ui.model.UIChatThread
import com.beeper.sms.app.ui.theme.getBeeperIndicatorColor
import com.beeper.sms.app.ui.util.formatInboxDate
import java.util.*

@ExperimentalFoundationApi
@Composable
fun InboxScreen(
    inboxState : InboxState,
    onConversationSelected : (String)->Unit,
    onBackButtonClicked : ()->Unit,
    onNewChatClicked : ()->Unit
) {
    Scaffold(topBar = {
        SMSAppBar("Inbox", onBackButtonClicked)
    }, floatingActionButton = {
        FloatingActionButton(backgroundColor = getBeeperIndicatorColor(), onClick = onNewChatClicked) {
            Icon(
               painter = rememberVectorPainter(image = Icons.Rounded.Chat),
                tint = Color.White,
               contentDescription = null
            )
        }
    }) {
        when(inboxState){
            InboxState.Error -> Text("Ouch: error loading the Inbox")
            InboxState.Loading -> Box(Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center){CircularProgressIndicator()}
            is InboxState.Loaded ->
                SMSThreadsSection(
                threads = inboxState.chats.map {
                    val firstNickName =   it.members.mapNotNull {
                            member-> member.value.nickname
                    }.firstOrNull()
                    val firstLetter =  firstNickName?.replaceFirstChar {
                        firstChar ->
                        if (firstChar.isLowerCase()) firstChar.titlecase(
                            Locale.getDefault()
                        ) else firstChar.toString()
                    }
                        ?.firstOrNull() ?: '#'
                    val firstAvatar =  it.members.mapNotNull {
                            member-> member.value.avatar
                    }.firstOrNull()

                    UIChatThread(
                        it.threadId,
                        it.getTitleFromMembers(),
                        it.snippet,
                        formatInboxDate(it.timestamp.toLong(), LocalContext.current),
                        getBeeperIndicatorColor(),
                        if(!firstLetter.isDigit()
                            && firstLetter != '+'
                            && firstLetter != '('){
                            firstLetter.toString()
                        }else{
                            "#"
                             },
                        firstAvatar,
                        it.isRead
                    )
                },
                onClick = { onConversationSelected(it.id) },
                onLongClick = {}
            )
        }
    }
}



