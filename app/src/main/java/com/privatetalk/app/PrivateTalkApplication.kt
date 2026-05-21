package com.privatetalk.app

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrivateTalkApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val appId = getString(R.string.onesignal_app_id)
        if (appId == "REPLACE_WITH_ONESIGNAL_APP_ID" || appId.isBlank()) {
            return
        }

        OneSignal.Debug.logLevel = LogLevel.WARN
        OneSignal.initWithContext(this, appId)

        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(false)
        }
    }

    companion object {
        fun isConfigured(context: android.content.Context): Boolean {
            val appId = context.getString(R.string.onesignal_app_id)
            return appId != "REPLACE_WITH_ONESIGNAL_APP_ID" && appId.isNotBlank()
        }
    }
}
