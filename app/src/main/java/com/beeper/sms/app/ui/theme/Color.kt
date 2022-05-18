package com.beeper.sms.app.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)

val TextTitleOnDarkColor =  Color(0xffE0E4E7)
val TextSubtitleOnDarkColor =  Color(0xff9FA2A4)

val TextTitleOnLightColor =  Color(0xFF3D3F41)
val TextSubtitleOnLightColor =  Color(0xFF979A9F)

val StartChatToolbarDarkColor = Color(0xff323244)
val StartChatToolbarLightColor =  Color(0xffefefff)


val SelectedContactChipBorderDarkColor = Color.LightGray
val SelectedContactChipBorderLightColor =  Color.DarkGray


val BeeperBase900DarkColor = Color(0xff25262d)
val BeeperBase900LightColor = Color(0xfff7f7f7)
val BeeperBase200DarkColor = Color(0xffefeff0)
val BeeperBase200LightColor = Color(0xff17191C)

val BeeperBase500DarkColor = Color(0xff949598)

val BeeperBase400DarkColor = Color(0xffc3c4c5)
val BeeperBase200GrayLightColor = Color(0xff737D8C)


val BeeperAzure500Color = Color(0xff4d82ee)
val BeeperBase700DarkColor = Color(0xff4f5056)

val BeeperIncomingMessageBGDarkColor = BeeperBase700DarkColor
val BeeperIncomingMessageBGLightColor = Color(0xFFEEEEEE)

@Composable
fun getToolbarBackgroundColor(): Color{
    return if(MaterialTheme.colors.isLight){
        StartChatToolbarLightColor
    }else{
        StartChatToolbarDarkColor
    }
}

@Composable
fun getSelectedContactChipBorderColor(): Color{
    return if(MaterialTheme.colors.isLight){
        SelectedContactChipBorderLightColor
    }else{
        SelectedContactChipBorderDarkColor
    }
}


@Composable
fun getTextTitleColor(): Color{
    return if(MaterialTheme.colors.isLight){
        TextTitleOnLightColor
    }else{
        TextTitleOnDarkColor
    }
}

@Composable
fun getTextSubtitleColor(): Color{
    return if(MaterialTheme.colors.isLight){
        TextSubtitleOnLightColor
    }else{
        TextSubtitleOnDarkColor
    }
}


@Composable
fun getBeeperContentPrimaryColor(): Color{
    return if(MaterialTheme.colors.isLight){
        BeeperBase200LightColor
    }else{
        BeeperBase200DarkColor
    }
}

@Composable
fun getBeeperContentSecondaryColor(): Color{
    return if(MaterialTheme.colors.isLight){
        BeeperBase200GrayLightColor
    }else{
        BeeperBase400DarkColor
    }
}

@Composable
fun getBeeperIndicatorColor(): Color{
    return if(MaterialTheme.colors.isLight){
        BeeperAzure500Color
    }else{
        BeeperAzure500Color
    }
}


@Composable
fun getFavoritesItemBackgroundColor(): Color{
    return if(MaterialTheme.colors.isLight){
        BeeperBase900LightColor
    }else{
        BeeperBase900DarkColor
    }
}

@Composable
fun getIncomingMessageBackgroundColor(): Color{
    return if(MaterialTheme.colors.isLight){
        BeeperIncomingMessageBGLightColor
    }else{
        BeeperIncomingMessageBGDarkColor
    }
}

@Composable
fun getMessageTextColor(isOutgoing : Boolean = false): Color{
    if(isOutgoing){
        return if(MaterialTheme.colors.isLight){
            Color.White
        }else{
            Color.White
        }
    }else{
        return if(MaterialTheme.colors.isLight){
            getBeeperContentPrimaryColor()
        }else{
            getBeeperContentPrimaryColor()
        }
    }
}

@Composable
fun getBeeperConversationBgColor(): Color{
    return if(MaterialTheme.colors.isLight){
        Color(0xffEFEFF0)
    }else{
        Color(0xff212128)
    }
}

@Composable
fun getBeeperConversationComposerOutlineColor(): Color{
    return if(MaterialTheme.colors.isLight){
        Color(0xff949598)
    }else{
        Color(0xff76767b)
    }
}

@Composable
fun getBeeperConversationComposerOutlineBgColor(): Color{
    return if(MaterialTheme.colors.isLight){
        Color(0xffbebfc0)
    }else{
        BeeperBase700DarkColor
    }
}

