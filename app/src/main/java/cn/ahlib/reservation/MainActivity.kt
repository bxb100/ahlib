package cn.ahlib.reservation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.ahlib.reservation.ui.ReservationApp
import cn.ahlib.reservation.ui.ReservationViewModel
import cn.ahlib.reservation.ui.theme.ReservationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = application as ReservationApplication
            val reservationViewModel: ReservationViewModel = viewModel(
                factory = ReservationViewModel.Factory(
                    repository = application.appContainer.repository,
                    readerQrCodeRepository =
                        application.appContainer.readerQrCodeRepository,
                    locationProvider = application.locationProvider,
                    shouldUseMockLocation =
                        application.automationManager::shouldUseMockLocation,
                    queueCalendarReminder =
                        application.automationManager::queueCalendarReminder,
                ),
            )
            ReservationTheme {
                ReservationApp(
                    viewModel = reservationViewModel,
                    automationManager = application.automationManager,
                    appUpdateManager = application.appUpdateManager,
                )
            }
        }
    }
}
