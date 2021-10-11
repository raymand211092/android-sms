package com.beeper.sms.commands.outgoing

data class BridgeStatus(
    var state_event: String,
    var error: String = "",
    var message: String = "",
    var remote_id: String? = null,
    var remote_name: String? = null,
) {
    constructor(state: State) : this(state.toString())

    enum class State(private val state: String) {
        STARTING("STARTING"),
        UNCONFIGURED("UNCONFIGURED"),
        CONNECTING("CONNECTING"),
        BACKFILLING("BACKFILLING"),
        CONNECTED("CONNECTED"),
        TRANSIENT_DISCONNECT("TRANSIENT_DISCONNECT"),
        BAD_CREDENTIALS("BAD_CREDENTIALS"),
        UNKNOWN_ERROR("UNKNOWN_ERROR"),
        LOGGED_OUT("LOGGED_OUT"),
        STOPPED("STOPPED");

        override fun toString() = state
    }
}
