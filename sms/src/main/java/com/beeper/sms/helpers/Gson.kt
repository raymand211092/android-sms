package com.beeper.sms.helpers

import com.beeper.sms.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.TimeSeconds
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement

object GsonHelper{
    val gson = newGson()
    private fun newGson(): Gson =
        GsonBuilder().registerTypeAdapter(TimeSeconds.GSON_TYPE, TimeSeconds.GSON_ADAPTER).create()
}

val Command.dataTree: JsonElement
    get() = GsonHelper.gson.toJsonTree(data)
fun <T>deserialize(command: Command, c: Class<T>): T =
    GsonHelper.gson.fromJson(command.dataTree, c)
        .apply { Log.d("GsonHelper", "deserialize #${command.id}: $this") }
