package com.vknxworks.arrctrl.data.net

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The live-queue addresses never carry the API key (it goes in the X-Api-Key header instead). */
class SignalRUrlTest {
    private val hub = "https://radarr.example.test/signalr/messages".toHttpUrl()

    @Test
    fun negotiateHasNoKey() {
        val url = signalRNegotiateUrl(hub)
        assertEquals("https://radarr.example.test/signalr/messages/negotiate?negotiateVersion=1", url.toString())
        assertNull(url.queryParameter("access_token"))
    }

    @Test
    fun socketHasOnlyTheConnectionToken() {
        val url = signalRSocketUrl(hub, "abc123")
        assertEquals("https://radarr.example.test/signalr/messages?id=abc123", url.toString())
        assertNull(url.queryParameter("access_token"))
        assertEquals(listOf("id"), url.queryParameterNames.toList())
    }
}
