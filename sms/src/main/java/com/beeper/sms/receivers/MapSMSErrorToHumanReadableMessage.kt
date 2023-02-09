import android.telephony.SmsManager

fun mapSMSErrorToHumanReadableMessage(error: Int): String{
    return when(error){
        SmsManager.RESULT_ERROR_RADIO_OFF -> "SMS radio is off"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "SMS service is currently unavailable"
        else -> "SMS network failure"
    }
}