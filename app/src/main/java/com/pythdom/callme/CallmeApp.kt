package com.pythdom.callme

import android.app.Application
import com.pythdom.callme.call.CallNotifications
import com.pythdom.callme.data.CallStore

class CallmeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallStore.init(this)
        CallNotifications.createChannels(this)
    }
}
