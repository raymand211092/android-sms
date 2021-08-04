package com.beeper.sms.provider

import com.beeper.sms.R
import kotlin.math.abs

data class ContactRow(
    var first_name: String? = null,
    var last_name: String? = null,
    var nickname: String? = null,
    var phoneNumber: String? = null,
    var phoneType: String? = null,
    var avatar: String? = null,
) {
    val contactLetter: Char
        get() =
            listOf(nickname, first_name, last_name).firstNotNullOfOrNull { it?.get(0) } ?: ' '

    val displayName: String
        get() = nickname ?: "${first_name ?: ""} ${last_name ?: ""}".trim()

    val contactColor: Int
        get() = colors[abs(phoneNumber?.hashCode() ?: hashCode()) % colors.size]

    companion object {
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
    }
}