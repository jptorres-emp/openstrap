package org.openstrap.app

import android.app.Application
import org.openstrap.app.ble.BleScanner
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.db.OpenStrapDatabase
import org.openstrap.app.data.settings.SettingsStore
import org.openstrap.app.device.StrapController
import org.openstrap.app.device.StrapManager
import org.openstrap.app.export.CsvExporter
import org.openstrap.app.health.HealthConnectExporter

/** Hand-wired dependencies; small enough not to need a DI framework. */
class AppContainer(val app: Application) {
    val database = OpenStrapDatabase.create(app)
    val settings = SettingsStore(app)
    val health = HealthRepository(database.healthDao())
    val scanner = BleScanner(app)
    val csv = CsvExporter(database.healthDao())
    val healthConnect = HealthConnectExporter(app, database.healthDao())
    val strap: StrapController = StrapManager(app, settings, database.healthDao())
}

class OpenStrapApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
