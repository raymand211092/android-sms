package com.beeper.sms.provider

import com.beeper.sms.provider.GuidProvider.Companion.normalize
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidProviderTest {
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
}