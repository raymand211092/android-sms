import android.telephony.SmsManager

fun mapMMSErrorToHumanReadableMessage(error: Int): String{
    return when(error){
        SmsManager.MMS_ERROR_INVALID_APN -> "APN failure"
        SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS-> "MMS network acquiring failure"
        SmsManager.MMS_ERROR_HTTP_FAILURE,SmsManager.MMS_ERROR_IO_ERROR-> "MMS transmission failure"
        SmsManager.MMS_ERROR_NO_DATA_NETWORK-> "No mobile data on the phone"
        else -> "MMS network failure"
    }
}