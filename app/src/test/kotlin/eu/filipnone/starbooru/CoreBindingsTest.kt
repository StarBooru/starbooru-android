package eu.filipnone.starbooru

import eu.filipnone.starbooru.core.isRandom
import eu.filipnone.starbooru.core.normalizeServer
import eu.filipnone.starbooru.core.pageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBindingsTest {
    @Test
    fun hostCoreLoads() {
        assertEquals(60u, pageSize())
    }

    @Test
    fun serversGetAScheme() {
        assertEquals("http://127.0.0.1:8085", normalizeServer("127.0.0.1:8085/"))
        assertEquals("https://booru.example", normalizeServer(" booru.example/api/v1 "))
        assertEquals("", normalizeServer("   "))
    }

    @Test
    fun randomOrderIsSeen() {
        assertTrue(isRandom("cat order:random"))
        assertFalse(isRandom("cat"))
    }
}
