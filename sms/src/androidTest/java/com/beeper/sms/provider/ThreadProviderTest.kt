package com.beeper.sms.provider

import com.beeper.sms.provider.ThreadProvider.Companion.chatGuid
import com.beeper.sms.provider.ThreadProvider.Companion.normalize
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadProviderTest {
    @Test
    fun googleMountainView() = assertEquals("+16502530000", "(650) 253-0000".normalize)

    @Test
    fun googleChicago() = assertEquals("+13128404100", "+1 312-840-4100".normalize)

    @Test
    fun googleBerlin() = assertEquals("+4930303986300", "+49 30 303986300".normalize)

    @Test
    fun googleBrazil() = assertEquals("+553121286800", "+55-31-2128-6800".normalize)

    @Test
    fun googleBangalore() = assertEquals("+918067218000", "+91-80-67218000".normalize)

    @Test
    fun googleDubai() = assertEquals("+97144509500", "+971 4 4509500".normalize)

    @Test
    fun googleTelAviv() = assertEquals("+972747466453", "+972-74-746-6453".normalize)

    @Test
    fun emailAddress() {
        // Address isn't necessarily a phone number, e.g. e-mail to SMS gateways
        assertEquals("user@example.com", "user@example.com".normalize)
    }

    @Test
    fun groupMessageGuid() {
        val user1 = "+18915554567"
        val user2 = "+16175556543"
        val user3 = "+13125554301"
        val guid = "SMS;+;+13125554301 +16175556543 +18915554567"
        assertEquals(guid, listOf(user1, user2, user3).chatGuid)
        assertEquals(guid, listOf(user1, user3, user2).chatGuid)
        assertEquals(guid, listOf(user2, user1, user3).chatGuid)
        assertEquals(guid, listOf(user2, user3, user1).chatGuid)
        assertEquals(guid, listOf(user3, user1, user2).chatGuid)
        assertEquals(guid, listOf(user3, user2, user1).chatGuid)
    }
}