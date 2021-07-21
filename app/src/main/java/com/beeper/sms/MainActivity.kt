package com.beeper.sms

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.beeper.sms.extensions.isDefaultSmsApp
import com.beeper.sms.extensions.requestSmsRoleIntent
import com.beeper.sms.ui.theme.BeeperSMSBridgeTheme


class MainActivity : ComponentActivity() {
    private var isDefault = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val roleResultHandler = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            Log.d(TAG, it.toString())
        }
        setContent {
            BeeperSMSBridgeTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isDefault.value) {
                        KillButton()
                    } else {
                        RequestPermissionButton {
                            roleResultHandler.launch(requestSmsRoleIntent())
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

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun KillButton() {
    Button(
        onClick = {
            Bridge.INSTANCE.stop()
        }
    ) {
        Text(text = "Kill mautrix-imessage")
    }
}

@Composable
fun RequestPermissionButton(action: () -> Unit) {
    Button(onClick = action) {
        Text(text = "Set default SMS app")
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BeeperSMSBridgeTheme {
        KillButton()
    }
}