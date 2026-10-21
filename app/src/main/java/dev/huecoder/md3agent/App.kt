package dev.huecoder.md3agent

import android.app.Application
import dev.huecoder.md3agent.system.notifications.AppNotifications
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The :python process only hosts PythonService; the app's database, settings and jobs live in the main process.
        if (getProcessName() != packageName) return
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(container.visibility)
        AppNotifications.registerChannels(this)
        container.applicationScope.launch { container.chats.purgeExpiredTrash() }
    }
}
