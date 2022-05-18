/*package com.beeper.sms.app

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beeper.sms.SmsMmsSender
import com.beeper.sms.StartStopBridge
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
import com.beeper.sms.work.WorkManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch


@ExperimentalComposeUiApi
@ExperimentalFoundationApi
@ExperimentalPermissionsApi
class StartStopMainActivity : ComponentActivity() {
    private var isDefault = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionResult =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if (result.values.all { it }) {
                    //Bridge.INSTANCE.start(this)
                }
            }

        setContent {
            val context = LocalContext.current
            val permissionsState =
                rememberMultiplePermissionsState(permissions = SMS_PERMISSIONS)
            val navController = rememberNavController()
            BeeperSMSBridgeTheme {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        Body(
                            isDefaultSmsApp = isDefault.value,
                            hasPermission = permissionsState.allPermissionsGranted,
                            startInitialSync = {
                                WorkManager(context).startSMSBridgeMinimalBackfill()

                            },
                            startBridge = { lifecycleScope.launch {
                                StartStopBridge.INSTANCE.start(
                                    this@StartStopMainActivity,
                                    timeoutMillis = StartStopBridge.DEFAULT_STARTUP_TIMEOUT_MILLIS
                                )
                            } },
                            stopBridge = {
                                lifecycleScope.launch {
                                    StartStopBridge.INSTANCE.stop()
                                }
                            },
                            pingBridge = {
                                WorkManager(context).startSMSBridgeStoreBridgedChats()
                                //StartStopBridge.INSTANCE.ping()
                                         },
                            selectDefaultSmsApp = {
                                startRequestDefaultSmsActivity()
                            },
                            requestPermissions = {
                                permissionResult.launch(
                                    SMS_PERMISSIONS.plus(DUAL_SIM_PERMISSION).toTypedArray()
                                )
                            },
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
 */