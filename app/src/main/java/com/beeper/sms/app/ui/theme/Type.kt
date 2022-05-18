package com.beeper.sms.app.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BeeperTypography = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    body1 = TextStyle(
        letterSpacing = 0.sp,
        fontSize = 14.sp,
    ),
    body2 = TextStyle(
        letterSpacing = 0.sp,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    h3 =  TextStyle(
        letterSpacing = 0.sp,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold
    ),
    h2 =  TextStyle(
        letterSpacing = 0.sp,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold
    ),
)

object BeeperCustomTypography {
    val inviteTextStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Normal,
    )
}