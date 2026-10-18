package com.vknxworks.arrctrl

import android.content.Context
import com.vknxworks.arrctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)
}

val Context.container: AppContainer
    get() = (applicationContext as ArrCtrlApp).container
