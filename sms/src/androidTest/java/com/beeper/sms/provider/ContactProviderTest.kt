package com.beeper.sms.provider

import android.provider.ContactsContract.Contacts
import androidx.core.net.toUri
import com.beeper.sms.provider.ContactProvider.Companion.searchUri
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactProviderTest {
    @Test
    fun encodeSearchUri() =
        assertEquals("${Contacts.CONTENT_FILTER_URI}/User%20Name".toUri(), "User Name".searchUri)

    @Test
    fun newlineSearch() =
        assertEquals(Contacts.CONTENT_URI, "\n".searchUri)

    @Test
    fun emptySearch() =
        assertEquals(Contacts.CONTENT_URI, "".searchUri)
}