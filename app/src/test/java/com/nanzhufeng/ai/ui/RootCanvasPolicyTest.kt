package com.nanzhufeng.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RootCanvasPolicyTest {
    @Test fun `conversation settings and document routes keep stable root canvases`() {
        assertEquals(RootCanvasKind.CONVERSATION, rootCanvasKind(P5ARoute.CAPTURE))
        assertEquals(RootCanvasKind.CONVERSATION, rootCanvasKind(P5ARoute.CONVERSATION))
        assertEquals(RootCanvasKind.DOCUMENT, rootCanvasKind(P5ARoute.OCR))
        P5ARoute.entries
            .filterNot { it in setOf(P5ARoute.CAPTURE, P5ARoute.CONVERSATION, P5ARoute.OCR) }
            .forEach { assertEquals(it.name, RootCanvasKind.SETTINGS, rootCanvasKind(it)) }
    }
}
