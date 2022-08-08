package com.beeper.sms.receivers

import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.beeper.sms.Log
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.extensions.printExtras
import com.klinker.android.send_message.DeliveredReceiver

class SmsDelivered : DeliveredReceiver() {
    override fun onMessageStatusUpdated(context: Context, intent: Intent?, resultCode: Int) {
        Log.d(TAG, "result: $resultCode extras: ${intent.printExtras()}")
    }

    companion object {
        private const val TAG = "MyDeliveredReceiver"
            private const val ERR_NETWORK_ERROR = "network_error"
            private const val ERR_TIMEOUT = "timeout"
            private const val ERR_UNSUPPORTED = "unsupported"

            fun Int.toError(intent: Intent?): Error {
                val message = errorToString(this, intent)
                return when (this) {
                    SmsManager.RESULT_ERROR_NO_SERVICE,
                    SmsManager.RESULT_ERROR_RADIO_OFF,
                    SmsManager.RESULT_RIL_NETWORK_NOT_READY,
                    SmsManager.RESULT_RADIO_NOT_AVAILABLE ->
                        Error(ERR_TIMEOUT, message)
                    SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
                    SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED ->
                        Error(ERR_UNSUPPORTED, message)
                    else ->
                        Error(ERR_NETWORK_ERROR, message)
                }
            }

            private fun errorToString(rc: Int, intent: Intent?): String {
                val errorCode = intent?.getStringExtra("errorCode")
                return when (rc) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "ERROR_GENERIC_FAILURE($errorCode)"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "ERROR_RADIO_OFF"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "ERROR_NULL_PDU"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "ERROR_NO_SERVICE"
                    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "ERROR_LIMIT_EXCEEDED"
                    SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "ERROR_FDN_CHECK_FAILURE"
                    SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "ERROR_SHORT_CODE_NOT_ALLOWED"
                    SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "ERROR_SHORT_CODE_NEVER_ALLOWED"
                    SmsManager.RESULT_RADIO_NOT_AVAILABLE -> "RADIO_NOT_AVAILABLE"
                    SmsManager.RESULT_NETWORK_REJECT -> "NETWORK_REJECT"
                    SmsManager.RESULT_INVALID_ARGUMENTS -> "INVALID_ARGUMENTS"
                    SmsManager.RESULT_INVALID_STATE -> "INVALID_STATE"
                    SmsManager.RESULT_NO_MEMORY -> "NO_MEMORY"
                    SmsManager.RESULT_INVALID_SMS_FORMAT -> "INVALID_SMS_FORMAT"
                    SmsManager.RESULT_SYSTEM_ERROR -> "SYSTEM_ERROR"
                    SmsManager.RESULT_MODEM_ERROR -> "MODEM_ERROR"
                    SmsManager.RESULT_NETWORK_ERROR -> "NETWORK_ERROR"
                    SmsManager.RESULT_ENCODING_ERROR -> "ENCODING_ERROR"
                    SmsManager.RESULT_INVALID_SMSC_ADDRESS -> "INVALID_SMSC_ADDRESS"
                    SmsManager.RESULT_OPERATION_NOT_ALLOWED -> "OPERATION_NOT_ALLOWED"
                    SmsManager.RESULT_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    SmsManager.RESULT_NO_RESOURCES -> "NO_RESOURCES"
                    SmsManager.RESULT_CANCELLED -> "CANCELLED"
                    SmsManager.RESULT_REQUEST_NOT_SUPPORTED -> "REQUEST_NOT_SUPPORTED"
                    SmsManager.RESULT_NO_BLUETOOTH_SERVICE -> "NO_BLUETOOTH_SERVICE"
                    SmsManager.RESULT_INVALID_BLUETOOTH_ADDRESS -> "INVALID_BLUETOOTH_ADDRESS"
                    SmsManager.RESULT_BLUETOOTH_DISCONNECTED -> "BLUETOOTH_DISCONNECTED"
                    SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING -> "UNEXPECTED_EVENT_STOP_SENDING"
                    SmsManager.RESULT_SMS_BLOCKED_DURING_EMERGENCY -> "SMS_BLOCKED_DURING_EMERGENCY"
                    SmsManager.RESULT_SMS_SEND_RETRY_FAILED -> "SMS_SEND_RETRY_FAILED"
                    SmsManager.RESULT_REMOTE_EXCEPTION -> "REMOTE_EXCEPTION"
                    SmsManager.RESULT_NO_DEFAULT_SMS_APP -> "NO_DEFAULT_SMS_APP"
                    SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE -> "RIL_RADIO_NOT_AVAILABLE"
                    SmsManager.RESULT_RIL_SMS_SEND_FAIL_RETRY -> "RIL_SMS_SEND_FAIL_RETRY"
                    SmsManager.RESULT_RIL_NETWORK_REJECT -> "RIL_NETWORK_REJECT"
                    SmsManager.RESULT_RIL_INVALID_STATE -> "RIL_INVALID_STATE"
                    SmsManager.RESULT_RIL_INVALID_ARGUMENTS -> "RIL_INVALID_ARGUMENTS"
                    SmsManager.RESULT_RIL_NO_MEMORY -> "RIL_NO_MEMORY"
                    SmsManager.RESULT_RIL_REQUEST_RATE_LIMITED -> "RIL_REQUEST_RATE_LIMITED"
                    SmsManager.RESULT_RIL_INVALID_SMS_FORMAT -> "RIL_INVALID_SMS_FORMAT"
                    SmsManager.RESULT_RIL_SYSTEM_ERR -> "RIL_SYSTEM_ERR"
                    SmsManager.RESULT_RIL_ENCODING_ERR -> "RIL_ENCODING_ERR"
                    SmsManager.RESULT_RIL_INVALID_SMSC_ADDRESS -> "RIL_INVALID_SMSC_ADDRESS"
                    SmsManager.RESULT_RIL_MODEM_ERR -> "RIL_MODEM_ERR"
                    SmsManager.RESULT_RIL_NETWORK_ERR -> "RIL_NETWORK_ERR"
                    SmsManager.RESULT_RIL_INTERNAL_ERR -> "RIL_INTERNAL_ERR"
                    SmsManager.RESULT_RIL_REQUEST_NOT_SUPPORTED -> "RIL_REQUEST_NOT_SUPPORTED"
                    SmsManager.RESULT_RIL_INVALID_MODEM_STATE -> "RIL_INVALID_MODEM_STATE"
                    SmsManager.RESULT_RIL_NETWORK_NOT_READY -> "RIL_NETWORK_NOT_READY"
                    SmsManager.RESULT_RIL_OPERATION_NOT_ALLOWED -> "RIL_OPERATION_NOT_ALLOWED"
                    SmsManager.RESULT_RIL_NO_RESOURCES -> "RIL_NO_RESOURCES"
                    SmsManager.RESULT_RIL_CANCELLED -> "RIL_CANCELLED"
                    SmsManager.RESULT_RIL_SIM_ABSENT -> "RIL_SIM_ABSENT"
                    121 -> "RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED"
                    122 -> "RIL_ACCESS_BARRED"
                    123 -> "RIL_BLOCKED_DUE_TO_CALL"
                    else -> "Unknown error ($rc, $errorCode)"
                }
            }
        }
    }