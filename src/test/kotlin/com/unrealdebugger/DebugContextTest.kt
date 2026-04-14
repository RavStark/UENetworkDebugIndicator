package com.unrealdebugger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Color

class DebugContextTest {

    @Test
    fun parseServer() {
        assertEquals(DebugContext.SERVER, DebugContext.fromString("Server"))
    }

    @Test
    fun parseClient() {
        assertEquals(DebugContext.CLIENT, DebugContext.fromString("Client"))
    }

    @Test
    fun parseListenServer() {
        assertEquals(DebugContext.LISTEN_SERVER, DebugContext.fromString("Listen Server"))
    }

    @Test
    fun parseCaseInsensitive() {
        assertEquals(DebugContext.SERVER, DebugContext.fromString("server"))
        assertEquals(DebugContext.CLIENT, DebugContext.fromString("CLIENT"))
        assertEquals(DebugContext.LISTEN_SERVER, DebugContext.fromString("listen server"))
    }

    @Test
    fun parseUnknownString() {
        assertEquals(DebugContext.UNKNOWN, DebugContext.fromString("SomethingElse"))
    }

    @Test
    fun parseEmptyString() {
        assertEquals(DebugContext.UNKNOWN, DebugContext.fromString(""))
    }

    @Test
    fun parseNull() {
        assertEquals(DebugContext.UNKNOWN, DebugContext.fromString(null))
    }

    @Test
    fun serverHasRedBackground() {
        assertEquals(Color(0xCC, 0x33, 0x33), DebugContext.SERVER.backgroundColor)
    }

    @Test
    fun clientHasBlueBackground() {
        assertEquals(Color(0x33, 0x66, 0xCC), DebugContext.CLIENT.backgroundColor)
    }

    @Test
    fun listenServerHasGoldBackground() {
        assertEquals(Color(0xCC, 0x99, 0x00), DebugContext.LISTEN_SERVER.backgroundColor)
    }

    @Test
    fun unknownHasGrayBackground() {
        assertEquals(Color(0x66, 0x66, 0x66), DebugContext.UNKNOWN.backgroundColor)
    }

    @Test
    fun labelsAreUppercase() {
        assertEquals("SERVER", DebugContext.SERVER.label)
        assertEquals("CLIENT", DebugContext.CLIENT.label)
        assertEquals("LISTEN SERVER", DebugContext.LISTEN_SERVER.label)
        assertEquals("UNKNOWN", DebugContext.UNKNOWN.label)
    }
}
