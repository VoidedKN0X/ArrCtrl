package com.vknxworks.arrctrl

import android.app.Application

class ArrCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
