package cn.ahlib.reservation.ui

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import cn.ahlib.reservation.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReaderQrCodePlaceholderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun loadingStateIsDisplayedWhileQrCodeIsFetched() {
        composeRule.setContent {
            MaterialTheme {
                ReaderQrCodePlaceholder()
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.reader_qr_loading))
            .assertIsDisplayed()
    }

    @Test
    fun viewerDoesNotExposeRawQrContentAsText() {
        val content = "opaque-reader-qr-content"
        composeRule.setContent {
            MaterialTheme {
                ReaderQrCodeViewer(
                    content = content,
                    onDismiss = {},
                )
            }
        }

        composeRule.onAllNodesWithText(content).assertCountEquals(0)
    }

    @Test
    fun failedLoadShowsRetryableNotSetState() {
        var retryCount = 0
        composeRule.setContent {
            MaterialTheme {
                ProfileScreen(
                    profile = null,
                    readerId = "reader-123",
                    readerQrContent = null,
                    appVersionName = "test",
                    isLoadingReaderQr = false,
                    isCheckingUpdate = false,
                    isLoggingOut = false,
                    onRetryReaderQrCode = { retryCount++ },
                    onOpenReaderQrCode = {},
                    onOpenAutomation = {},
                    onCheckUpdate = {},
                    onLogout = {},
                )
            }
        }

        composeRule
            .onAllNodesWithContentDescription(
                context.getString(R.string.reader_qr_description),
            )
            .assertCountEquals(0)
        composeRule
            .onAllNodesWithText(context.getString(R.string.reader_qr_loading))
            .assertCountEquals(0)
        composeRule
            .onNodeWithText(context.getString(R.string.reader_qr_not_set))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, retryCount)
        }
    }
}
