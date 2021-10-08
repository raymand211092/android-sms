package com.beeper.sms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beeper.sms.Bridge
import com.beeper.sms.app.ui.theme.BeeperSMSBridgeTheme
import com.beeper.sms.extensions.SMS_PERMISSIONS
import com.beeper.sms.extensions.hasPermissions
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


class MainActivity : ComponentActivity() {
    private var isDefault = mutableStateOf(false)

    @ExperimentalPermissionsApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionResult = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                Bridge.INSTANCE.start(this)
            }
        }
        setContent {
            BeeperSMSBridgeTheme {
                val permissionsState =
                    rememberMultiplePermissionsState(permissions = SMS_PERMISSIONS)
                Body(
                    hasPermission = permissionsState.allPermissionsGranted,
                    signOut = { Bridge.INSTANCE.signOut(this@MainActivity) },
                    stopBridge = { Bridge.INSTANCE.stop(this@MainActivity) },
                    startBridge = { Bridge.INSTANCE.start(this@MainActivity) },
                    killBridge = { Bridge.INSTANCE.killProcess() },
                    requestPermissions = { permissionResult.launch(SMS_PERMISSIONS.toTypedArray()) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        isDefault.value = hasPermissions
    }
}

@Composable
fun Body(
    hasPermission: Boolean,
    signOut: () -> Unit = {},
    stopBridge: () -> Unit = {},
    startBridge: () -> Unit = {},
    killBridge: () -> Unit = {},
    requestPermissions: () -> Unit = {},
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
                    Button("Start bridge", action = startBridge)
                    Button("Stop bridge", action = stopBridge)
                    if (BuildConfig.DEBUG) {
                        Button("Kill bridge", action = killBridge)
                    }
                    Button("Sign out", spacer = false, action = signOut)
                } else {
                    RequestPermissionButton(requestPermissions)
                }
            }
        }
    )
}

@Composable
fun Button(text: String, spacer: Boolean = true, action: () -> Unit) {
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
        Body(false)
    }
}

@Preview
@Composable
fun PreviewDefaultDark() {
    BeeperSMSBridgeTheme(darkTheme = true) {
        Body(true)
    }
}

@Preview
@Composable
fun PreviewNotDefault() {
    BeeperSMSBridgeTheme {
        Body(false)
    }
}

@Preview
@Composable
fun PreviewDefault() {
    BeeperSMSBridgeTheme {
        Body(true)
    }
}

