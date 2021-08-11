package com.beeper.sms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beeper.sms.Bridge
import com.beeper.sms.app.theme.BeeperSMSBridgeTheme
import com.beeper.sms.extensions.isDefaultSmsApp
import com.beeper.sms.extensions.requestSmsRoleIntent


class MainActivity : ComponentActivity() {
    private var isDefault = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeeperSMSBridgeTheme {
                Body(
                    isDefault = isDefault.value,
                    signOut = { Bridge.INSTANCE.signOut() },
                    killMautrix = { Bridge.INSTANCE.stop() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        isDefault.value = isDefaultSmsApp
    }
}

@Composable
fun Body(
    isDefault: Boolean,
    signOut: () -> Unit,
    killMautrix: () -> Unit,
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
                if (isDefault) {
                    KillButton(killMautrix)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = signOut) {
                        Text(text = "Sign out")
                    }
                } else {
                    RequestPermissionButton()
                }
            }
        }
    )
}

@Composable
fun KillButton(action: () -> Unit) {
    if (BuildConfig.DEBUG) {
        Button(onClick = action) {
            Text(text = "Kill mautrix-imessage")
        }
    }
}

@Composable
fun RequestPermissionButton() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {}
    )
    Button(onClick = { launcher.launch(context.requestSmsRoleIntent()) }) {
        Text(text = stringResource(id = R.string.set_default_sms_app))
    }
}

@Preview
@Composable
fun PreviewNotDefaultDark() {
    BeeperSMSBridgeTheme(darkTheme = true) {
        Body(false, {}, {})
    }
}

@Preview
@Composable
fun PreviewDefaultDark() {
    BeeperSMSBridgeTheme(darkTheme = true) {
        Body(true, {}, {})
    }
}

@Preview
@Composable
fun PreviewNotDefault() {
    BeeperSMSBridgeTheme {
        Body(false, {}, {})
    }
}

@Preview
@Composable
fun PreviewDefault() {
    BeeperSMSBridgeTheme {
        Body(true, {}, {})
    }
}

