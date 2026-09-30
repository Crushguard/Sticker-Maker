package com.piptechnologies.stickermaker.core.telemetry

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactionTest {

    @Test
    fun keepsTypesAndStacksButNoMessages() {
        val cause = IOException("Could not move pack own-np-0123456789ab into place")
        val failure = IllegalStateException("sticker pack name: Aymen \u2764 Sara", cause)
        val redacted = failure.redacted()
        generateSequence(redacted) { it.cause }.forEach { t ->
            assertFalse(t.toString(), "own-np-" in t.toString() || "Sara" in t.toString())
        }
        assertEquals("java.lang.IllegalStateException", redacted.message)
        assertEquals("java.io.IOException", redacted.cause?.message)
        assertArrayEquals(failure.stackTrace, redacted.stackTrace)
    }
}
