
fun mapMMSErrorToHumanReadableMessage(error: Int): String{
    return when(error){
        2-> "SMS radio is off"
        4-> "SMS service is currently unavailable"
        else -> "SMS network failure"
    }
}