package com.beeper.sms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.beeper.sms.ui.theme.BeeperSMSBridgeTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeeperSMSBridgeTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KillButton()
                }
            }
        }
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BeeperSMSBridgeTheme {
        KillButton()
    }
}