package au.com.shiftyjelly.pocketcasts.utils.extensions

import junit.framework.TestCase.assertEquals
import org.junit.Test

class StringTest {
    @Test
    fun `md5 of empty string`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", "".md5())
    }

    @Test
    fun `md5 matches known digest`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", "abc".md5())
    }

    @Test
    fun `md5 is lowercase hex padded to 32 characters`() {
        val digest = "https://feeds.example.com/show.xml".md5()

        requireNotNull(digest)
        assertEquals(32, digest.length)
        assertEquals(digest.lowercase(), digest)
        assertEquals(true, digest.all { it in "0123456789abcdef" })
    }

    @Test
    fun `md5 of single element list matches md5 of that string`() {
        assertEquals("abc".md5(), listOf("abc").md5())
    }
}
