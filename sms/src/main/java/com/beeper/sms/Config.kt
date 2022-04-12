package com.beeper.sms

data class Config(
    var homeserver: Homeserver? = null,
    var appservice: AppService? = null,
    var imessage: IMessage? = null,
    var bridge: Bridge? = null,
    var logging: Logging? = null,
) {
    data class Homeserver(
        var address: String? = null,
        var websocket_proxy: String? = null,
        var domain: String? = null,
        var asmux: Boolean? = null,
    )

    data class AppService(
        var database: String? = null,
        var id: String? = null,
        var bot: Bot? = null,
        var as_token: String? = null,
        var hs_token: String? = null,
    )

    data class Bot(
        var username: String? = null,
        var displayname: String? = null,
        var avatar: String? = null,
    )

    data class IMessage(
        var platform: String? = null,
    )

    data class Bridge(
        var user: String? = null,
        var username_template: String? = null,
        var displayname_template: String? = null,
        var delivery_receipts: Boolean? = null,
        var sync_with_custom_puppets: Boolean? = null,
        var sync_direct_chat_list: Boolean? = null,
        var login_shared_secret: String? = null,
        var chat_sync_max_age: Float? = null,
        var initial_backfill_limit: Int? = null,
        var periodic_sync: Boolean? = null,
        var find_portals_if_db_empty: Boolean? = null,
        var media_viewer_url: String? = null,
        var media_viewer_sms_min_size: Int? = null,
        var command_prefix: String? = null,
        var encryption: Encryption? = null,
        var send_error_notices: Boolean? = null,
        var message_status_events: Boolean? = null,
        var max_handle_seconds: Int? = null,
    )

    data class Encryption(
        var allow: Boolean? = null,
        var default: Boolean? = null,
        var appservice: Boolean? = null,
        var key_sharing: KeySharing? = null,
    )

    data class KeySharing(
        var allow: Boolean? = null,
        var require_cross_signing: Boolean? = null,
        var require_verification: Boolean? = null,
    )

    data class Logging(
        var directory: String? = null,
        var file_name_format: String? = null,
        var file_date_format: String? = null,
        var file_mode: Int? = null,
        var timestamp_format: String? = null,
        var print_level: String? = null,
    )
}
