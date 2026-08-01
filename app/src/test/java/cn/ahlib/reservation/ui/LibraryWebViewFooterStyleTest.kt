package cn.ahlib.reservation.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryWebViewFooterStyleTest {
    @Test
    fun footerFixInstallsPersistentStyleRule() {
        assertTrue(
            FOOTER_COMPOSITING_FIX_SCRIPT.contains(
                "document.createElement('style')",
            ),
        )
        assertTrue(
            FOOTER_COMPOSITING_FIX_SCRIPT.contains(
                ".wap-footer {",
            ),
        )
        assertTrue(
            FOOTER_COMPOSITING_FIX_SCRIPT.contains(
                "window.__ahlibFooterCompositingFixObserver",
            ),
        )
        assertFalse(
            FOOTER_COMPOSITING_FIX_SCRIPT.contains(
                "querySelector('.wap-footer')",
            ),
        )
    }
}
