package dev.huecoder.md3agent.assistant

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenContextStoreTest {
    private var now = 0L
    private val store = ScreenContextStore { now }

    @Test
    fun `a snapshot expires after a few minutes`() {
        store.recordScreenshot(null)
        now += 4 * 60_000L
        assertNotNull(store.fresh())
        now += 2 * 60_000L
        assertNull(store.fresh())
        assertNull(store.latest.value)
    }
}
