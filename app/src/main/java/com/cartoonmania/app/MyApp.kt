package com.cartoonmania.app

import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}
