package com.beeper.sms.commands

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Command(
    var command: String,
    var data: @RawValue Any?,
    var id: Int? = null,
) : Parcelable