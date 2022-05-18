package com.beeper.sms.app

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beeper.sms.Bridge
import com.beeper.sms.SmsMmsSender
import com.beeper.sms.app.ui.screen.conversation.ConversationScreen
import com.beeper.sms.app.ui.screen.conversation.ConversationViewModel
import com.beeper.sms.app.ui.screen.inbox.InboxScreen
import com.beeper.sms.app.ui.screen.inbox.InboxViewModel
import com.beeper.sms.app.ui.screen.newchat.NewChatScreen
import com.beeper.sms.app.ui.screen.newchat.NewChatViewModel
import com.beeper.sms.app.ui.screen.newchat.SelectionMode
import com.beeper.sms.app.ui.theme.BeeperSMSBridgeTheme
import com.beeper.sms.extensions.DUAL_SIM_PERMISSION
import com.beeper.sms.extensions.SMS_PERMISSIONS
import com.beeper.sms.extensions.isDefaultSmsApp
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.ChatThreadProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


@ExperimentalComposeUiApi
@ExperimentalFoundationApi
@ExperimentalPermissionsApi
class MainActivity : ComponentActivity() {
    private var isDefault = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionResult =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if (result.values.all { it }) {
                    Bridge.INSTANCE.start(this)
                }
            }

        setContent {
            val permissionsState =
                rememberMultiplePermissionsState(permissions = SMS_PERMISSIONS)
            val navController = rememberNavController()
            BeeperSMSBridgeTheme {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        Body(
                            isDefaultSmsApp = isDefault.value,
                            hasPermission = permissionsState.allPermissionsGranted,
                            signOut = { Bridge.INSTANCE.signOut(this@MainActivity) },
                            stopBridge = { Bridge.INSTANCE.stop(this@MainActivity) },
                            startBridge = { Bridge.INSTANCE.start(this@MainActivity) },
                            killBridge = { Bridge.INSTANCE.killProcess() },
                            selectDefaultSmsApp = {
                                startRequestDefaultSmsActivity()
                            },
                            requestPermissions = {
                                permissionResult.launch(
                                    SMS_PERMISSIONS.plus(DUAL_SIM_PERMISSION).toTypedArray()
                                )
                            },
                            pingBridge = { Bridge.INSTANCE.ping() },
                            startNewChat = {
                                navController.navigate("startNewChat")
                            },
                            openInbox = {
                                navController.navigate("inbox")
                            }
                        )
                    }
                    composable("inbox") {
                        val model: InboxViewModel = viewModel(
                            factory = InboxViewModel.Factory(
                                ChatThreadProvider(applicationContext)
                            )
                        )

                        val state = model.state.collectAsState()

                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colors.background
                        ) {
                            val hasPermission = permissionsState.allPermissionsGranted

                            if (hasPermission) {
                                InboxScreen(
                                    state.value,
                                    onConversationSelected = { conversationId ->
                                        navController.navigate("conversation/$conversationId")
                                    },
                                    onBackButtonClicked = {
                                        navController.popBackStack()
                                    },
                                    onNewChatClicked = {
                                        navController.navigate("startNewChat")
                                    })
                            } else {
                                RequestPermissionButton {
                                    permissionResult.launch(
                                        SMS_PERMISSIONS.plus(DUAL_SIM_PERMISSION).toTypedArray()
                                    )
                                }
                            }
                        }
                    }
                    composable("conversation/{conversationId}") {
                        val conversationId = it.arguments?.getString("conversationId")
                        if (conversationId != null) {
                            val messageProvider = MessageProvider(applicationContext)
                            val model: ConversationViewModel = viewModel(
                                factory = ConversationViewModel.Factory(
                                    conversationId,
                                    ChatThreadProvider(applicationContext),
                                    messageProvider,
                                    SmsMmsSender(applicationContext, messageProvider)
                                )
                            )
                            val state = model.state.collectAsState()
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colors.background
                            ) {
                                ConversationScreen(conversationState = state.value,
                                    onBackClicked = {
                                        navController.popBackStack()
                                    },
                                    sendMessage = { message ->
                                        model.sendMessage(message)
                                    }, sendFile = { uri ->
                                        model.sendMessage(uri)
                                    })
                            }
                        } else {
                            navController.popBackStack()
                        }

                    }
                    composable("startNewChat") {
                        val model: NewChatViewModel = viewModel(
                            factory = NewChatViewModel.Factory(
                                ContactProvider(applicationContext),
                                ChatThreadProvider(applicationContext)
                            )
                        )

                        val newChatState = model.newChatState.collectAsState()
                        val text = model.filterText.collectAsState()
                        val keyboardMode = model.keyboardMode.collectAsState()
                        val selectionMode = model.selectionMode.collectAsState()
                        val selectedContacts = model.selectedContacts.collectAsState()
                        val customContactState = model.customContactState.collectAsState()

                        BackHandler(enabled =
                        selectionMode.value is SelectionMode.Group, onBack = {
                            model.onBackPressed()
                        })

                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colors.background
                        ) {
                            val hasPermission = permissionsState.allPermissionsGranted

                            if (hasPermission) {
                                NewChatScreen(
                                    newChatState.value,
                                    text.value,
                                    keyboardMode.value,
                                    selectionMode.value,
                                    selectedContacts.value,
                                    customContactState.value,
                                    model::onTextChanged,
                                    onContactSelected = {

                                        model.onContactSelected(it)
                                        val selectedNumber =
                                            model.shouldNavigateAfterContactSelected()
                                        if (selectedNumber != null) {
                                            val threadId = model.getThreadId(setOf(selectedNumber))
                                            navController.navigate("conversation/$threadId") {
                                                popUpTo("startNewChat") {
                                                    inclusive = true
                                                }
                                            }
                                        }

                                    },
                                    model::onKeyboardModeChanged,
                                    model::onGroupModeClicked,
                                    model::onEmptyBackspacePressed,
                                    onBackButtonClicked = {
                                        onBackPressed()
                                    },
                                    onNextClicked = {
                                        val selectedNumbers =
                                            model.shouldNavigateAfterGroupSelected()
                                        if (selectedNumbers.isNotEmpty()) {
                                            val threadId = model.getThreadId(selectedNumbers)
                                            navController.navigate("conversation/$threadId") {
                                                popUpTo("startNewChat") {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    }
                                )
                            } else {
                                RequestPermissionButton {
                                    permissionResult.launch(
                                        SMS_PERMISSIONS.plus(DUAL_SIM_PERMISSION).toTypedArray()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefault.value = isDefaultSmsApp
    }

    private fun startRequestDefaultSmsActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivity(intent)
                }
            } else {
                //Can't be the default SMS app
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
        }

    }

}

@Composable
fun Body(
    isDefaultSmsApp: Boolean,
    hasPermission: Boolean,
    signOut: () -> Unit = {},
    startInitialSync: () -> Unit = {},
    stopBridge: () -> Unit = {},
    startBridge: () -> Unit = {},
    killBridge: () -> Unit = {},
    selectDefaultSmsApp: () -> Unit = {},
    requestPermissions: () -> Unit = {},
    pingBridge: () -> Unit = {},
    startNewChat: () -> Unit = {},
    openInbox: () -> Unit = {},
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) }
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hasPermission) {
                    if(!isDefaultSmsApp){
                        Button(onClick = {
                            selectDefaultSmsApp()
                        }) {
                            Text(text = stringResource(id = R.string.select_default_sms_app))
                        }
                    }else {
                        HomeButton("Inbox", action = openInbox)
                        HomeButton("Start new chat", action = startNewChat)
                        HomeButton("Start initial sync", action = startInitialSync)
                        HomeButton("Start bridge", action = startBridge)
                        HomeButton("Ping bridge", action = pingBridge)
                        HomeButton("Stop bridge", action = stopBridge)
                        if (BuildConfig.DEBUG) {
                            HomeButton("Kill bridge", action = killBridge)
                        }
                        HomeButton("Sign out", spacer = false, action = signOut)
                    }
                } else {
                    RequestPermissionButton(requestPermissions)
                }
            }
        }
    )
}

@Composable
fun HomeButton(text: String, spacer: Boolean = true, action: () -> Unit) {
    Button(onClick = action) {
        Text(text = text)
    }
    if (spacer) {
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RequestPermissionButton(requestPermissions: () -> Unit) {
    Button(onClick = requestPermissions) {
        Text(text = stringResource(id = R.string.request_permissions))
    }
}

@Preview
@Composable
fun PreviewNotDefaultDark() {
    BeeperSMSBridgeTheme(darkTheme = true) {
        Body(false,false)
    }
}

@Preview
@Composable
fun PreviewDefaultDark() {
    BeeperSMSBridgeTheme(darkTheme = true) {
        Body(false,true)
    }
}

@Preview
@Composable
fun PreviewNotDefault() {
    BeeperSMSBridgeTheme {
        Body(false,false)
    }
}

@Preview
@Composable
fun PreviewDefault() {
    BeeperSMSBridgeTheme {
        Body(false,true)
    }
}

