package com.survivalcoding.a510

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.Utility
import android.util.Log
import com.survivalcoding.a510.services.RetrofitClient
import com.survivalcoding.a510.services.chat.ImageCleanupWorker
import com.survivalcoding.a510.utils.DataIndexManager

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, "a167264ee18a4fc52fd2238b8c445a39")
        val keyHash = Utility.getKeyHash(this)
        Log.e("KeyHash", "키해시 값: $keyHash")

        RetrofitClient.initialize(this)
        ImageCleanupWorker.schedule(this)
//        DataIndexManager.init(this)
    }
}