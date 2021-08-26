package com.beeper.sms

import timber.log.Timber

internal object Log {
    fun d(tag: String, message: String) = log(tag, message, Timber::d)

    fun e(tag: String, message: String) = log(tag, message, Timber::e)

    fun e(tag: String, throwable: Throwable) = log(tag, throwable, Timber::e)

    fun v(tag: String, message: String) = log(tag, message, Timber::v)

    fun w(tag: String, message: String) = log(tag, message, Timber::w)

    private fun <T> log(tag: String, payload: T, log: (T) -> Unit) {
        Timber.tag("SMS-$tag")
        log(payload)
    }
}
