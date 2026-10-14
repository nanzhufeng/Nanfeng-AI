package com.nanzhufeng.ai.domain
import org.junit.Assert.assertEquals
import org.junit.Test
class PreviewBitmapBudgetTest {
    @Test fun `long screenshots and camera images have bounded decode allocation`() {
        assertEquals(1,previewBitmapSampleSize(1200,1800))
        assertEquals(4,previewBitmapSampleSize(4000,16000))
        assertEquals(4,previewBitmapSampleSize(8000,6000))
        assertEquals(0,previewBitmapSampleSize(0,6000))
        assertEquals(0,previewBitmapSampleSize(-1,6000))
    }
}
