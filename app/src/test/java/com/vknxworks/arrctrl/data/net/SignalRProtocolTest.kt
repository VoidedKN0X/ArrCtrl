package com.vknxworks.arrctrl.data.net

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalRProtocolTest {
    private val rs = SignalRProtocol.SEPARATOR

    @Test fun theHandshakeAnswerMeansReady() {
        assertEquals(listOf(LiveEvent.READY), SignalRProtocol.events("{}$rs"))
        assertEquals(listOf(LiveEvent.CLOSE), SignalRProtocol.events("{\"error\":\"Requested protocol 'x' is not available.\"}$rs"))
    }

    @Test fun queueMessagesAreRecognised() {
        fun message(name: String) = "{\"type\":1,\"target\":\"receiveMessage\",\"arguments\":[{\"name\":\"$name\",\"body\":{\"action\":\"sync\"}}]}$rs"
        assertEquals(listOf(LiveEvent.QUEUE_CHANGED), SignalRProtocol.events(message("queue")))
        assertEquals(listOf(LiveEvent.QUEUE_CHANGED), SignalRProtocol.events(message("queue/details")))
        assertEquals(listOf(LiveEvent.QUEUE_CHANGED), SignalRProtocol.events(message("queue/status")))
        assertEquals(listOf(LiveEvent.OTHER), SignalRProtocol.events(message("movie")))
        assertEquals(listOf(LiveEvent.OTHER), SignalRProtocol.events(message("version")))
    }

    @Test fun oneFrameCanCarrySeveralMessages() {
        val frame = "{\"type\":6}$rs{\"type\":1,\"target\":\"receiveMessage\",\"arguments\":[{\"name\":\"queue\"}]}$rs{\"type\":7}$rs"
        assertEquals(listOf(LiveEvent.OTHER, LiveEvent.QUEUE_CHANGED, LiveEvent.CLOSE), SignalRProtocol.events(frame))
    }

    @Test fun anythingElseIsIgnored() {
        assertEquals(emptyList<LiveEvent>(), SignalRProtocol.events(""))
        assertEquals(listOf(LiveEvent.OTHER), SignalRProtocol.events("not json$rs"))
        assertEquals(listOf(LiveEvent.OTHER), SignalRProtocol.events("[1,2]$rs"))
        assertEquals(listOf(LiveEvent.OTHER), SignalRProtocol.events("{\"type\":1,\"target\":\"somethingElse\",\"arguments\":[{\"name\":\"queue\"}]}$rs"))
    }
}
