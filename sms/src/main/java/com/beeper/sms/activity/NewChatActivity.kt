package com.beeper.sms.activity

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.beeper.sms.Bridge
import com.beeper.sms.R
import com.beeper.sms.activity.NewChatActivity.Companion.isPhoneNumber
import com.beeper.sms.activity.ui.theme.BeeperSMSBridgeTheme
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.Chat
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.ContactRow
import com.beeper.sms.provider.ThreadProvider.Companion.chatGuid
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

class NewChatActivity : ComponentActivity() {
    private lateinit var contactProvider: ContactProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactProvider = ContactProvider(applicationContext)

        setContent {
            BeeperSMSBridgeTheme {
                val scope = rememberCoroutineScope()
                var currentJob by remember { mutableStateOf<Job?>(null) }
                var text by remember { mutableStateOf("") }
                var contacts by remember { mutableStateOf(emptyList<ContactRow>()) }
                SideEffect {
                    currentJob?.cancel()
                    currentJob = scope.launch {
                        contacts = contactProvider.searchContacts(text)
                    }
                }
                Body(
                    text = text,
                    contacts = contacts,
                    back = onBackPressedDispatcher::onBackPressed,
                    search = { text = it },
                    onClick = { c ->
                        val room =
                            contactProvider
                                .getContacts(listOfNotNull(c.phoneNumber))
                                .map { contact -> contact.nickname }
                                .joinToString()
                        Bridge.INSTANCE.send(
                            Command(
                                command = "chat",
                                data = Chat(
                                    chat_guid = listOfNotNull(c.phoneNumber!!).chatGuid,
                                    title = room,
                                    members = listOfNotNull(c.phoneNumber!!)
                                )
                            )
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        internal const val TAG = "NewChatActivity"

        internal val String.isPhoneNumber: Boolean
            get() = Patterns.PHONE.matcher(this).matches()
    }
}

@Composable
fun Body(
    text: String,
    contacts: List<ContactRow>,
    back: () -> Unit,
    search: (String) -> Unit,
    onClick: (ContactRow) -> Unit,
) {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = MaterialTheme.colors.isLight
    val appBarColor = colorResource(R.color.app_bar)
    SideEffect {
        systemUiController.setSystemBarsColor(
            color = appBarColor,
            darkIcons = useDarkIcons
        )
    }
    Scaffold(
        topBar = {
            Surface(elevation = 12.dp, modifier = Modifier.background(color = appBarColor)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BackButton(back)
                        Text(
                            modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 0.dp),
                            text = stringResource(R.string.new_conversation),
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onSurface,
                            style = MaterialTheme.typography.h6,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.new_conversation_to),
                            color = MaterialTheme.colors.onSurface,
                            modifier = Modifier.padding(16.dp, 16.dp, 20.dp, 16.dp)
                        )
                        SearchInput(text, search)
                    }
                }
            }
        },
        content = {
            val sendTo = if (text.isPhoneNumber) {
                val name = stringResource(id = R.string.send_to, text)
                listOf(ContactRow(first_name = name, phoneNumber = text))
            } else {
                emptyList()
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(sendTo + contacts) {
                    Contact(it, onClick = onClick)
                }
            }
        }
    )
}

@Composable
fun SearchInput(
    text: String,
    search: (String) -> Unit
) {
    var dialpad by remember { mutableStateOf(false) }
    TextField(
        modifier = Modifier.fillMaxWidth(),
        value = text,
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            textColor = MaterialTheme.colors.onSurface,
        ),
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = if (dialpad) KeyboardType.Number else KeyboardType.Text
        ),
        onValueChange = { search(it) },
        placeholder = { Text(text = stringResource(R.string.new_conversation_hint)) },
        trailingIcon = {
            IconButton(onClick = { dialpad = !dialpad }) {
                Icon(
                    painter = painterResource(
                        if (dialpad) {
                            R.drawable.ic_outline_keyboard_24
                        } else {
                            R.drawable.ic_outline_dialpad_24
                        }
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colors.onSurface,
                )
            }
        }
    )
}

@Composable
fun Contact(c: ContactRow, onClick: (ContactRow) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(c) }
            .padding(0.dp, 8.dp)
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .padding(16.dp, 0.dp)
                .clip(CircleShape),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            c.avatar
                ?.let { ContactAvatar(uri = it) }
                ?: ContactLetter(c)
        }
        Column(modifier = Modifier.padding(0.dp, 0.dp, 16.dp, 0.dp)) {
            Text(text = c.displayName, style = MaterialTheme.typography.body1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = c.phoneNumber!!,
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Text(
                    text = c.phoneType ?: "",
                    style = MaterialTheme.typography.subtitle2,
                )
            }
        }
    }
}

@Composable
fun ContactAvatar(uri: String) {
    Box(modifier = Modifier.size(40.dp)) {
        Image(
            painter = rememberImagePainter(data = uri),
            modifier = Modifier.align(Alignment.Center),
            contentDescription = null,
        )
    }
}

@Composable
fun ContactLetter(c: ContactRow) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(colorResource(id = colors[abs(c.contactHash) % colors.size])),
    ) {
        Text(
            text = c.contactLetter.toString(),
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
        )
    }
}

@Composable
fun BackButton(back: () -> Unit) {
    IconButton(onClick = back) {
        Icon(
            painter = painterResource(R.drawable.ic_outline_arrow_back_24),
            tint = MaterialTheme.colors.onSurface,
            contentDescription = null,
        )
    }
}

@Composable
fun StartChatButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        text = { Text(text = stringResource(id = R.string.start_chat)) },
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_outline_chat_24),
                contentDescription = null
            )
        },
        onClick = onClick,
        modifier = Modifier.padding(8.dp)
    )
}

private val colors = listOf(
    R.color.tomato,
    R.color.tangerine,
    R.color.pumpkin,
    R.color.mango,
    R.color.banana,
    R.color.citron,
    R.color.avocado,
    R.color.pistachio,
    R.color.basil,
    R.color.sage,
    R.color.peacock,
    R.color.cobalt,
    R.color.lavender,
    R.color.wisteria,
    R.color.amethyst,
    R.color.grape,
    R.color.radicchio,
    R.color.cherry_blossom,
    R.color.flamingo,
    R.color.graphite,
    R.color.birch,
)

private val previewUser1 = ContactRow(
    first_name = "Test",
    last_name = "User",
    phoneNumber = "+13125551234",
    phoneType = "Work"
)

private val previewUser2 = ContactRow(
    first_name = "Test",
    last_name = "User",
    phoneNumber = "+13125551234",
    phoneType = "Work"
)

@Preview
@Composable
fun DefaultPreviewDark() {
    BeeperSMSBridgeTheme(darkTheme = true) {
        Body("", listOf(previewUser1, previewUser2), {}, {}) {}
    }
}

@Preview
@Composable
fun DefaultPreview() {
    BeeperSMSBridgeTheme {
        Body("", listOf(previewUser1, previewUser2), {}, {}) {}
    }
}
