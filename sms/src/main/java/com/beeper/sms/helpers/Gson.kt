package com.beeper.sms.helpers

import com.beeper.sms.commands.TimeSeconds
import com.google.gson.Gson
import com.google.gson.GsonBuilder

fun newGson(): Gson =
    GsonBuilder().registerTypeAdapter(TimeSeconds.GSON_TYPE, TimeSeconds.GSON_ADAPTER).create()