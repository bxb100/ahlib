package cn.ahlib.reservation

import android.app.Application
import cn.ahlib.reservation.automation.AutomationManager
import cn.ahlib.reservation.data.AppContainer
import cn.ahlib.reservation.location.DeviceLocationProvider

class ReservationApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    lateinit var locationProvider: DeviceLocationProvider
        private set

    lateinit var automationManager: AutomationManager
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        locationProvider = DeviceLocationProvider(this)
        automationManager = AutomationManager(
            context = this,
        )
        automationManager.sync()
    }
}
