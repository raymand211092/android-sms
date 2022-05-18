package com.beeper.sms.app.ui.components.external

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.beeper.sms.app.ui.theme.BeeperBase200DarkColor
import com.beeper.sms.app.ui.theme.getBeeperContentPrimaryColor

private fun Int.textDp(density: Density): TextUnit = with(density) {
    this@textDp.dp.toSp()
}
val Int.textDp: TextUnit
    @Composable get() =  this.textDp(density = LocalDensity.current)

const val TIMESTAMP_FONT_SIZE = 10

@Composable
fun Timestamp(time: String, isOutgoing: Boolean,textAlpha : Float = 0.7f, color : Color = if(isOutgoing){
    BeeperBase200DarkColor.copy(alpha = textAlpha)
    }else{
        getBeeperContentPrimaryColor().copy(alpha = textAlpha)
    } ) {
    Text(
            text = time,
            color = color,
            fontSize = TIMESTAMP_FONT_SIZE.textDp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
    )
}
