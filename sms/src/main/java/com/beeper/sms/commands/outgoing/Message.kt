package com.beeper.sms.commands.outgoing

import android.net.Uri
import android.telephony.SmsManager
import com.beeper.sms.BuildConfig
import com.beeper.sms.commands.TimeSeconds

data class Message(
    var guid: String,
    var timestamp: TimeSeconds,
    var subject: String,
    var text: String,
    var chat_guid: String,
    var thread_id: String,
    var sender_guid: String?,
    var is_from_me: Boolean = false,
    var thread_originator_guid: String? = null,
    var thread_originator_part: Int? = null,
    var attachments: List<Attachment>? = null,
    var associated_message: List<AssociatedMessage>? = null,
    var group_action_type: Int? = null,
    var new_group_title: String? = null,
    @Transient var is_mms: Boolean = false,
    @Transient var resp_st: Int? = null,
    @Transient var rowId: Long,
    @Transient var uri: Uri? = null,
    @Transient var subId: Int? = null,
    @Transient var messageStatus: MessageStatus? = null,
    var is_read: Boolean? = null,
    @Transient var sender_recipient_id: String? = null,

    ) {
    data class Attachment(
        var mime_type: String?,
        var file_name: String,
        var path_on_disk: String,
        @Transient var media_thumbnail_height: Int? = null,
        @Transient var media_thumbnail_width: Int? = null

    )

    data class AssociatedMessage(
        var target_guid: String,
        var type: Int,
    )

    override fun toString(): String {
        return "Message(guid='$guid', timestamp=$timestamp, subject='${if (BuildConfig.DEBUG) subject else "<redacted>"}', text='${if (BuildConfig.DEBUG) text else "<redacted>"}', chat_guid='$chat_guid', sender_guid=$sender_guid, is_from_me=$is_from_me, thread_originator_guid=$thread_originator_guid, thread_originator_part=$thread_originator_part, attachments=$attachments, associated_message=$associated_message, group_action_type=$group_action_type, new_group_title=$new_group_title, is_mms=$is_mms, resp_st=$resp_st, rowId=$rowId, uri=$uri, subId=$subId)"
    }
}


sealed class MessageStatus{
    object Sent : MessageStatus()
    object Waiting : MessageStatus()
    data class Failed(val errorCode: MessageErrorCode) : MessageStatus()
}

sealed class MessageErrorCode{
    sealed class SMSErrorCode : MessageErrorCode() {
        object RadioOff : SMSErrorCode()
        object NoSMSService : SMSErrorCode()
        object ShortcodeNotAllowed : SMSErrorCode()
        object GenericFailure : SMSErrorCode()
    }

    sealed class MMSErrorCode : MessageErrorCode() {
        object NoMMSService : MMSErrorCode()
        object MMSNetworkError : MMSErrorCode()
        object MMSIOError : MMSErrorCode()
        object MMSErrorRetry : MMSErrorCode()
    }

    data class OtherFailure(val errorCode: Int, val isMMS: Boolean) : MessageErrorCode()

    object ErrorTypeNotStored : MessageErrorCode()

    companion object {
        fun fromSmsResult(resultCode: Int?): MessageErrorCode {
            return when (resultCode) {
                SmsManager.RESULT_ERROR_RADIO_OFF -> SMSErrorCode.RadioOff
                SmsManager.RESULT_ERROR_NO_SERVICE -> SMSErrorCode.NoSMSService
                SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
                SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> SMSErrorCode.ShortcodeNotAllowed
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> SMSErrorCode.GenericFailure
                null -> ErrorTypeNotStored
                else -> OtherFailure(resultCode, false)
                /* Documentation on MMS error result codes
            /**
             * No error.
             */
            public static final int RESULT_ERROR_NONE    = 0;
                /** Generic failure cause */
                public static final int RESULT_ERROR_GENERIC_FAILURE    = 1;
            /** Failed because radio was explicitly turned off */
            public static final int RESULT_ERROR_RADIO_OFF          = 2;
                /** Failed because no pdu provided */
                public static final int RESULT_ERROR_NULL_PDU           = 3;
            /** Failed because service is currently unavailable */
            public static final int RESULT_ERROR_NO_SERVICE         = 4;
                /** Failed because we reached the sending queue limit. */
                public static final int RESULT_ERROR_LIMIT_EXCEEDED     = 5;
            /**
             * Failed because FDN is enabled.
             */
            public static final int RESULT_ERROR_FDN_CHECK_FAILURE  = 6;
                /** Failed because user denied the sending of this short code. */
                public static final int RESULT_ERROR_SHORT_CODE_NOT_ALLOWED = 7;
            /** Failed because the user has denied this app ever send premium short codes. */
            public static final int RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED = 8;
                /**
                 * Failed because the radio was not available
                 */
                public static final int RESULT_RADIO_NOT_AVAILABLE = 9;
            /**
             * Failed because of network rejection
             */
            public static final int RESULT_NETWORK_REJECT = 10;
                /**
                 * Failed because of invalid arguments
                 */
                public static final int RESULT_INVALID_ARGUMENTS = 11;
            /**
             * Failed because of an invalid state
             */
            public static final int RESULT_INVALID_STATE = 12;
                /**
                 * Failed because there is no memory
                 */
                public static final int RESULT_NO_MEMORY = 13;
            /**
             * Failed because the sms format is not valid
             */
            public static final int RESULT_INVALID_SMS_FORMAT = 14;
                /**
                 * Failed because of a system error
                 */
                public static final int RESULT_SYSTEM_ERROR = 15;
            /**
             * Failed because of a modem error
             */
            public static final int RESULT_MODEM_ERROR = 16;
                /**
                 * Failed because of a network error
                 */
                public static final int RESULT_NETWORK_ERROR = 17;
            /**
             * Failed because of an encoding error
             */
            public static final int RESULT_ENCODING_ERROR = 18;
                /**
                 * Failed because of an invalid smsc address
                 */
                public static final int RESULT_INVALID_SMSC_ADDRESS = 19;
            /**
             * Failed because the operation is not allowed
             */
            public static final int RESULT_OPERATION_NOT_ALLOWED = 20;
                /**
                 * Failed because of an internal error
                 */
                public static final int RESULT_INTERNAL_ERROR = 21;
            /**
             * Failed because there are no resources
             */
            public static final int RESULT_NO_RESOURCES = 22;
                /**
                 * Failed because the operation was cancelled
                 */
                public static final int RESULT_CANCELLED = 23;
            /**
             * Failed because the request is not supported
             */
            public static final int RESULT_REQUEST_NOT_SUPPORTED = 24;
                /**
                 * Failed sending via bluetooth because the bluetooth service is not available
                 */
                public static final int RESULT_NO_BLUETOOTH_SERVICE = 25;
            /**
             * Failed sending via bluetooth because the bluetooth device address is invalid
             */
            public static final int RESULT_INVALID_BLUETOOTH_ADDRESS = 26;
                /**
                 * Failed sending via bluetooth because bluetooth disconnected
                 */
                public static final int RESULT_BLUETOOTH_DISCONNECTED = 27;
            /**
             * Failed sending because the user denied or canceled the dialog displayed for a premium
             * shortcode sms or rate-limited sms.
             */
            public static final int RESULT_UNEXPECTED_EVENT_STOP_SENDING = 28;
                /**
                 * Failed sending during an emergency call
                 */
                public static final int RESULT_SMS_BLOCKED_DURING_EMERGENCY = 29;
            /**
             * Failed to send an sms retry
             */
            public static final int RESULT_SMS_SEND_RETRY_FAILED = 30;
                /**
                 * Set by BroadcastReceiver to indicate a remote exception while handling a message.
                 */
                public static final int RESULT_REMOTE_EXCEPTION = 31;
            /**
             * Set by BroadcastReceiver to indicate there's no default sms app.
             */
            public static final int RESULT_NO_DEFAULT_SMS_APP = 32;
                // Radio Error results
                /**
                 * The radio did not start or is resetting.
                 */
                public static final int RESULT_RIL_RADIO_NOT_AVAILABLE = 100;
            /**
             * The radio failed to send the sms and needs to retry.
             */
            public static final int RESULT_RIL_SMS_SEND_FAIL_RETRY = 101;
                /**
                 * The sms request was rejected by the network.
                 */
                public static final int RESULT_RIL_NETWORK_REJECT = 102;
            /**
             * The radio returned an unexpected request for the current state.
             */
            public static final int RESULT_RIL_INVALID_STATE = 103;
                /**
                 * The radio received invalid arguments in the request.
                 */
                public static final int RESULT_RIL_INVALID_ARGUMENTS = 104;
            /**
             * The radio didn't have sufficient memory to process the request.
             */
            public static final int RESULT_RIL_NO_MEMORY = 105;
                /**
                 * The radio denied the operation due to overly-frequent requests.
                 */
                public static final int RESULT_RIL_REQUEST_RATE_LIMITED = 106;
            /**
             * The radio returned an error indicating invalid sms format.
             */
            public static final int RESULT_RIL_INVALID_SMS_FORMAT = 107;
                /**
                 * The radio encountered a platform or system error.
                 */
                public static final int RESULT_RIL_SYSTEM_ERR = 108;
            /**
             * The SMS message was not encoded properly.
             */
            public static final int RESULT_RIL_ENCODING_ERR = 109;
                /**
                 * The specified SMSC address was invalid.
                 */
                public static final int RESULT_RIL_INVALID_SMSC_ADDRESS = 110;
            /**
             * The vendor RIL received an unexpected or incorrect response.
             */
            public static final int RESULT_RIL_MODEM_ERR = 111;
                /**
                 * The radio received an error from the network.
                 */
                public static final int RESULT_RIL_NETWORK_ERR = 112;
            /**
             * The modem encountered an unexpected error scenario while handling the request.
             */
            public static final int RESULT_RIL_INTERNAL_ERR = 113;
                /**
                 * The request was not supported by the radio.
                 */
                public static final int RESULT_RIL_REQUEST_NOT_SUPPORTED = 114;
            /**
             * The radio cannot process the request in the current modem state.
             */
            public static final int RESULT_RIL_INVALID_MODEM_STATE = 115;
                /**
                 * The network is not ready to perform the request.
                 */
                public static final int RESULT_RIL_NETWORK_NOT_READY = 116;
            /**
             * The radio reports the request is not allowed.
             */
            public static final int RESULT_RIL_OPERATION_NOT_ALLOWED = 117;
                /**
                 * There are insufficient resources to process the request.
                 */
                public static final int RESULT_RIL_NO_RESOURCES = 118;
            /**
             * The request has been cancelled.
             */
            public static final int RESULT_RIL_CANCELLED = 119;
                /**
                 * The radio failed to set the location where the CDMA subscription
                 * can be retrieved because the SIM or RUIM is absent.
                 */
                public static final int RESULT_RIL_SIM_ABSENT = 120;
            /**
             * 1X voice and SMS are not allowed simulteneously.
             */
            public static final int RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED = 121;
                /**
                 * Access is barred.
                 */
                public static final int RESULT_RIL_ACCESS_BARRED = 122;
            /**
             * SMS is blocked due to call control, e.g., resource unavailable in the SMR entity.
             */
            public static final int RESULT_RIL_BLOCKED_DUE_TO_CALL = 123;
                /**
                 * A RIL error occurred during the SMS send.
                 */
                public static final int RESULT_RIL_GENERIC_ERROR = 124;
            // SMS receiving results sent as a "result" extra in {@link Intents.SMS_REJECTED_ACTION}
            /**
             * SMS receive dispatch failure.
             */
            public static final int RESULT_RECEIVE_DISPATCH_FAILURE = 500;
                /**
                 * SMS receive injected null PDU.
                 */
                public static final int RESULT_RECEIVE_INJECTED_NULL_PDU = 501;
            /**
             * SMS receive encountered runtime exception.
             */
            public static final int RESULT_RECEIVE_RUNTIME_EXCEPTION = 502;
                /**
                 * SMS received null message from the radio interface layer.
                 */
                public static final int RESULT_RECEIVE_NULL_MESSAGE_FROM_RIL = 503;
            /**
             * SMS short code received while the phone is in encrypted state.
             */
            public static final int RESULT_RECEIVE_WHILE_ENCRYPTED = 504;
                /**
                 * SMS receive encountered an SQL exception.
                 */
                public static final int RESULT_RECEIVE_SQL_EXCEPTION = 505;
            /**
             * SMS receive an exception parsing a uri.
             */
            public static final int RESULT_RECEIVE_URI_EXCEPTION = 506;
             */
            }
        }

        fun fromMmsResult(resultCode: Int?): MessageErrorCode {
            return when (resultCode) {
                SmsManager.MMS_ERROR_HTTP_FAILURE -> MMSErrorCode.MMSNetworkError
                SmsManager.MMS_ERROR_IO_ERROR -> MMSErrorCode.MMSIOError
                SmsManager.MMS_ERROR_NO_DATA_NETWORK -> MMSErrorCode.NoMMSService
                null -> ErrorTypeNotStored
                else -> OtherFailure(resultCode, false)
            }
            /* Documentation on MMS error result codes
        /**
         * Unspecific MMS error occurred during send/download.
         */
        public static final int MMS_ERROR_UNSPECIFIED = 1;
        /**
         * ApnException occurred during MMS network setup.
         */
        public static final int MMS_ERROR_INVALID_APN = 2;
        /**
         * An error occurred during the MMS connection setup.
         */
        public static final int MMS_ERROR_UNABLE_CONNECT_MMS = 3;
        /**
         * An error occurred during the HTTP client setup.
         */
        public static final int MMS_ERROR_HTTP_FAILURE = 4;
        /**
         * An I/O error occurred reading the PDU.
         */
        public static final int MMS_ERROR_IO_ERROR = 5;
        /**
         * An error occurred while retrying sending/downloading the MMS.
         */
        public static final int MMS_ERROR_RETRY = 6;
        /**
         * The carrier-dependent configuration values could not be loaded.
         */
        public static final int MMS_ERROR_CONFIGURATION_ERROR = 7;
        /**
         * There is no data network.
         */
        public static final int MMS_ERROR_NO_DATA_NETWORK = 8;
        /**
         * The subscription id for the send/download is invalid.
         */
        public static final int MMS_ERROR_INVALID_SUBSCRIPTION_ID = 9;
        /**
         * The subscription id for the send/download is inactive.
         */
        public static final int MMS_ERROR_INACTIVE_SUBSCRIPTION = 10;
        /**
         * Data is disabled for the MMS APN.
         */
        public static final int MMS_ERROR_DATA_DISABLED = 11;
        */
        }
    }
}