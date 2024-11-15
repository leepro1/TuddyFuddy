package com.survivalcoding.a510.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.survivalcoding.a510.MainActivity
import com.survivalcoding.a510.R
import com.survivalcoding.a510.data.TokenManager
import com.survivalcoding.a510.services.chat.ChatService

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate() {
        super.onCreate()
        // TokenManager 초기화
        tokenManager = TokenManager(this)
    }

    // 1. FCM 토큰이 갱신될 때 호출되어 로컬에만 저장
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 새 토큰을 로컬에만 저장
        tokenManager.saveFCMToken(token)
        Log.d(TAG, "New FCM token saved locally")
    }

    // 2. 푸시 메시지를 수신했을 때 호출
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received: ${remoteMessage.data}")

        // data 메시지만 처리
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)

            val messageContent = remoteMessage.data["message"] ?: return
            val aiName = remoteMessage.data["aiName"] ?: return
            val title = aiName

            sendNotification(title, messageContent, remoteMessage.data)
        }
    }

    // 4. 데이터 메시지 처리
    private fun handleDataMessage(data: Map<String, String>) {
        Log.d(TAG, "💬 Data Message: $data")
        Log.d(TAG, "\n=== Data Message ===\n${data.entries.joinToString("\n")}\n==================")
    }

    // 5. 알림 생성 및 표시
    private fun sendNotification(title: String?, messageBody: String?, data: Map<String, String>) {
        // 푸시 알림 데이터에서 채팅방 ID 찾기
        val chatRoomId = data["roomId"]?.toIntOrNull()

        // 현재 사용자 화면에 떠있는 채팅방 ID 찾기
        val currentChatRoomId = ChatService.getActiveChatRoom()

        // 현재 사용자 화면과 같은 roomId라면 푸시알림 안보내기
        if (chatRoomId != null && chatRoomId == currentChatRoomId) {
            return
        }

        val channelId = "default_channel"

        // 인텐트에 채팅방 ID 추가
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // 알림에서 시작되었다는 플래그 추가
            putExtra("fromNotification", true)
            // 채팅방 ID 추가
            putExtra("chatRoomId", chatRoomId)
        }

        // PendingIntent의 requestCode를 chatRoomId로 설정하여 각 채팅방마다 고유한 PendingIntent 생성
        val pendingIntent = PendingIntent.getActivity(
            this,
            chatRoomId ?: 0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알림 스타일 설정
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.test_logo2)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android Oreo 이상에서는 채널 생성이 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 ID를 채팅방 ID로 설정하여 각 채팅방마다 개별 알림 표시
        notificationManager.notify(chatRoomId ?: 0, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "FCMService"
    }
}