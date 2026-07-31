package cn.ahlib.reservation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.ahlib.reservation.ReservationApplication
import cn.ahlib.reservation.ui.theme.ReservationTheme

class LibraryWebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as ReservationApplication
        val sessionCookies = application.appContainer.repository.webViewCookies(
            LIBRARY_RESERVATIONS_URL,
        )
        setContent {
            ReservationTheme(darkTheme = false) {
                LibraryWebViewScreen(
                    sessionCookies = sessionCookies,
                    onClose = ::finish,
                )
            }
        }
    }
}
