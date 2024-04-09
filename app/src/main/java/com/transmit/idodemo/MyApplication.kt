package com.transmit.idodemo

import android.app.Application
import com.transmit.authentication.TSAuthentication
import com.transmit.idosdk.TSIdo


class MyApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        TSAuthentication.initializeSDK(applicationContext)
        TSIdo.initializeSDK(applicationContext)
    }
}