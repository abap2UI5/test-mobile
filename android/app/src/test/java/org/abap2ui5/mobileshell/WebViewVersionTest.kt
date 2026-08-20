package org.abap2ui5.mobileshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WebView version floor (Phase 4). An unparseable version must stay null:
 * "unknown" is not "too old", and warning about a WebView we could not
 * identify would train users to ignore the warning.
 */
class WebViewVersionTest {

    @Test
    fun `full chromium version yields its major`() {
        assertEquals(120, WebViewVersion.majorVersion("120.0.6099.43"))
    }

    @Test
    fun `bare major is accepted`() {
        assertEquals(99, WebViewVersion.majorVersion("99"))
    }

    @Test
    fun `whitespace is tolerated`() {
        assertEquals(118, WebViewVersion.majorVersion(" 118.0.1 "))
    }

    @Test
    fun `unknown version names stay null`() {
        assertNull(WebViewVersion.majorVersion(null))
        assertNull(WebViewVersion.majorVersion(""))
        assertNull(WebViewVersion.majorVersion("dev-build"))
        assertNull(WebViewVersion.majorVersion("0.0.0.0"))
    }
}
