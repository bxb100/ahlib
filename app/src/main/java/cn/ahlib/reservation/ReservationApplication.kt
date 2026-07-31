package cn.ahlib.reservation

import android.app.Application
import cn.ahlib.reservation.automation.AutomationManager
import cn.ahlib.reservation.data.AppContainer
import cn.ahlib.reservation.location.DeviceLocationProvider
import cn.ahlib.reservation.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReservationApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var appContainer: AppContainer
        private set

    lateinit var locationProvider: DeviceLocationProvider
        private set

    lateinit var automationManager: AutomationManager
        private set

    lateinit var appUpdateManager: AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this, applicationScope)
        locationProvider = DeviceLocationProvider(this)
        automationManager = AutomationManager(
            context = this,
        )
        appUpdateManager = AppUpdateManager(
            context = this,
            scope = applicationScope,
        )
        applicationScope.launch {
            automationManager.sync()
        }
    }
}
