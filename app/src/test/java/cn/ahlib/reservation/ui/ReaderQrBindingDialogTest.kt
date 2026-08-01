package cn.ahlib.reservation.ui

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class ReaderQrBindingDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun automaticFetchButtonInvokesCallback() {
        var fetchCount = 0
        composeRule.setContent {
            MaterialTheme {
                ReaderQrBindingDialog(
                    pageUrl = "",
                    isSaving = false,
                    isFetching = false,
                    errorText = null,
                    onPageUrlChange = {},
                    onSave = {},
                    onFetch = { fetchCount++ },
                    onDismiss = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.reader_qr_auto_fetch))
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, fetchCount)
        }
    }

    @Test
    fun automaticFetchButtonIsDisabledWhileFetching() {
        composeRule.setContent {
            MaterialTheme {
                ReaderQrBindingDialog(
                    pageUrl = "",
                    isSaving = false,
                    isFetching = true,
                    errorText = null,
                    onPageUrlChange = {},
                    onSave = {},
                    onFetch = {},
                    onDismiss = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.reader_qr_auto_fetching))
            .assertIsNotEnabled()
    }

    @Test
    fun manualAuthorizationTabShowsManualBindingControls() {
        composeRule.setContent {
            MaterialTheme {
                ReaderQrBindingDialog(
                    pageUrl = "https://opac.ahlib.com/opac/m/reader/qrcode",
                    isSaving = false,
                    isFetching = false,
                    errorText = null,
                    onPageUrlChange = {},
                    onSave = {},
                    onFetch = {},
                    onDismiss = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.reader_qr_authorization_manual))
            .performClick()

        composeRule
            .onNodeWithText(context.getString(R.string.reader_qr_link_instructions))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.reader_qr_save))
            .assertIsEnabled()
        composeRule
            .onAllNodesWithText(context.getString(R.string.reader_qr_auto_fetch))
            .assertCountEquals(0)
    }
}
