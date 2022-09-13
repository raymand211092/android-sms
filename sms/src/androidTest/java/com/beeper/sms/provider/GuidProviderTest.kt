package com.beeper.sms.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidProviderTest {
    @Test
    fun googleMountainView() = assertEquals("+16502530000", GuidProvider.normalizeAddress("(650) 253-0000"))

    @Test
    fun googleChicago() = assertEquals("+13128404100", GuidProvider.normalizeAddress("+1 312-840-4100"))

    @Test
    fun googleBerlin() = assertEquals("+4930303986300", GuidProvider.normalizeAddress("+49 30 303986300"))

    @Test
    fun googleBrazil() = assertEquals("+553121286800", GuidProvider.normalizeAddress("+55-31-2128-6800"))

    @Test
    fun googleBangalore() = assertEquals("+918067218000", GuidProvider.normalizeAddress("+91-80-67218000"))

    @Test
    fun googleDubai() = assertEquals("+97144509500", GuidProvider.normalizeAddress("+971 4 4509500"))

    @Test
    fun googleTelAviv() = assertEquals("+972747466453", GuidProvider.normalizeAddress("+972-74-746-6453"))

    @Test
    fun emailAddress() {
        // Address isn't necessarily a phone number, e.g. e-mail to SMS gateways
        assertEquals("user@example.com", GuidProvider.normalizeAddress("user@example.com"))
    }

    @Test
    fun emailAddressWithDisplayName() =
        assertEquals("user@example.com", GuidProvider.normalizeAddress("User Name <user@example.com>"))

    @Test
    fun emailAddressWithQuotedDisplayName() =
        assertEquals("user@example.com", GuidProvider.normalizeAddress("\"User Name\" <user@example.com>"))

    @Test
    fun emailAddressWithSubdomain() =
        assertEquals("user@example.co.uk", GuidProvider.normalizeAddress("user@example.co.uk"))

    @Test
    fun emailAddressWithoutAngleBrackets() =
        assertEquals("user@example.com", GuidProvider.normalizeAddress("abc user@example.com 123"))

    @Test
    fun encodeSingleQuotations() = assertEquals("ab%27c", GuidProvider.normalizeAddress("ab'c"))

    @Test
    fun encodeDoubleQuotations() = assertEquals("ab%22c", GuidProvider.normalizeAddress("ab\"c"))

    @Test
    //Input of this tests fails when starting a new chat if the dashes aren't removed
    fun stripDashesIfNumber() = assertEquals("991976040", GuidProvider.normalizeAddress("9919-760-40"))

    @Test
    fun encodeWhitespace() = assertEquals("wz%20data", GuidProvider.normalizeAddress("wz data"))

    @Test
    fun decodeWhitespace() = assertEquals("wz data", GuidProvider.removeEscapingFromGuid("wz%20data"))

    @Test
    fun preserveDashesIfNotANumber() = assertEquals("wz-data", GuidProvider.normalizeAddress("wz-data"))
}