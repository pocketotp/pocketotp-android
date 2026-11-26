package com.pocketotp.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

class PocketOtpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RelayHttp.appContext = applicationContext
        RelayStart.tryStart(this, "app_start")
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    started.incrementAndGet()
                }

                override fun onActivityStopped(activity: Activity) {
                    started.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }

    companion object {
        private val started = AtomicInteger(0)

        fun isInForeground(): Boolean = started.get() > 0
    }
}
