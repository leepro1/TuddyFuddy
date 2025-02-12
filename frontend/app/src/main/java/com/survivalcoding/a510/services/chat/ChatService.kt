package com.survivalcoding.a510.services.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.survivalcoding.a510.repositories.chat.ChatDatabase
import com.survivalcoding.a510.repositories.chat.ChatMessage
import com.survivalcoding.a510.services.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// 백그라운드에서 챗API 작동하기 위한 클래
class ChatService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Room 데이터베이스 및 DAO 객체들을 지연 초기홧 설정
    private val database by lazy { ChatDatabase.getDatabase(applicationContext) }
    private val messageDao by lazy { database.chatMessageDao() }
    private val chatInfoDao by lazy { database.chatInfoDao() }
    // Retrofit을 사용한 AI 채팅 서비스 인터페이스 초기화
    private val aiChatService by lazy { RetrofitClient.aiChatService }

    // 서비스 생성 시 호출되는 콜백 함수 (필요한 초기화 작업을 수행)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel() // 알림 채널 생성하기
        startForeground(NOTIFICATION_ID, createNotification()) // 백그라운드 시작
    }

    // 서비스 생성 시 호출되는 콜백 함수
    // Intent를 통해 전달된 데이터를 처리하고 채팅 요청을 시작
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_CHAT -> {
                    val roomId = it.getIntExtra(EXTRA_ROOM_ID, -1)
                    val content = it.getStringExtra(EXTRA_CONTENT) ?: return@let
                    val loadingMessageId = if (it.hasExtra(EXTRA_LOADING_MESSAGE_ID)) {
                        it.getLongExtra(EXTRA_LOADING_MESSAGE_ID, -1)
                    } else null
                    handleChatRequest(roomId, content, loadingMessageId)
                }
            }
        }
        // 서비스가 강제 종료되더라도 자동으로 다시 재시작 시키
        return START_STICKY
    }

    // 챗API로 요청보내면 AI 답변 생성 요청을 처리하는 함수
    private fun handleChatRequest(roomId: Int, content: String, loadingMessageId: Long?) {
        serviceScope.launch {
            try {
                // 이미지 생성 요청 확인
                val imageRegex = Regex("(사진|이미지).*(보여|보내|생성)줘$")
                if (imageRegex.find(content) != null) {
                    val imagePrompt = content.replace(imageRegex, "").trim()
                    Log.d(TAG, "이미지 생성 요청 감지: $imagePrompt")

                    try {
                        // 이미지 생성 요청
                        val imageResponse = ImageMakerService.generateImage(imagePrompt)
                        Log.d(TAG, "이미지 생성 응답: ${imageResponse.isSuccessful}")

                        if (imageResponse.isSuccessful && imageResponse.body() != null) {
                            // 로딩 메시지 삭제
                            loadingMessageId?.let { id ->
                                messageDao.deleteMessageById(id)
                            }

                            val imageUrl = imageResponse.body()!!.result
                            Log.d(TAG, "생성된 이미지 URL: $imageUrl")

                            // 이미지 URL을 포함한 AI 메시지 저장
                            messageDao.insertMessage(
                                ChatMessage(
                                    roomId = roomId,
                                    content = "",  // 이미지일 때는 빈 문자열
                                    isAiMessage = true,
                                    isImage = true,  // 이미지임을 표시
                                    imageUrl = imageUrl  // 이미지 URL 저장
                                )
                            )

                            // ChatInfo 업데이트
                            chatInfoDao.updateLastMessage(
                                chatId = roomId,
                                message = "친구가 사진을 보냈습니다.",
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "이미지 생성 실패", e)
                        handleError(roomId, "이미지 생성에 실패했습니다. 다시 시도해주세요.")
                    }
                }

                // 디버그용 로그
                Log.d("ChatService", "받은 loadingMessageId: $loadingMessageId")
                Log.d("roomId","방 아이디 roomId@@@@@@@@@ : $roomId")
                Log.d("content","내용 content@@@@@@@@@ : $content")

                if (roomId == 5) {
                    // 로딩 메시지가 있으면 삭제
                    loadingMessageId?.let { id ->
                        messageDao.deleteMessageById(id)
                    }

                    // AI가 답변하는 여러가지 경우 케이스 6개 만들기
                    val patterns = listOf(
                        Pair(listOf(3, 4), false),    // 3번 - 4번 순으로 호출하기 (둘다 사용자 메시지에 응답)
                        Pair(listOf(4, 3), false),    // 4번 - 3번 순으로 호출하기 (둘다 사용자 메시지에 응답)
                        Pair(listOf(3), false),       // 3번만 호출하기
                        Pair(listOf(4), false),       // 4번만 호출하기
                        Pair(listOf(3, 4), true),     // 3번 호출하고 3번 응답으로 4번 호출하기 (AI끼리 대답시키기)
                        Pair(listOf(4, 3), true),      // 4번 호출하고 4번 응답으로 3번 호출하기(AI끼리 대답시키기)

                        // 아래 내용은 AI 2명 모두 대답하는 케이스에 가중치 주기 위해서 케이스 추
                        Pair(listOf(3, 4), false),    // 3번 - 4번 순으로 호출하기 (둘다 사용자 메시지에 응답)
                        Pair(listOf(4, 3), false),    // 4번 - 3번 순으로 호출하기 (둘다 사용자 메시지에 응답)
                        Pair(listOf(3, 4), true),     // 3번 호출하고 3번 응답으로 4번 호출하기 (AI끼리 대답시키기)
                        Pair(listOf(4, 3), true)      // 4번 호출하고 4번 응답으로 3번 호출하기(AI끼리 대답시키기)
                    )

                    // 6가지 케이스 중 하나를 랜덤으로 고르기
                    val selectedPattern = patterns.random()
                    Log.d("선택된 패턴 케이스", "AI 순서=${selectedPattern.first}, AI끼리 응답=${selectedPattern.second}")

                    val types = selectedPattern.first
                    val isChainedResponse = selectedPattern.second
                    nextResponderId = types.firstOrNull()

                    var previousResponse = content  // 처음엔 사용자 메시지로 초기화

                    // 랜덤으로 정해진 케이스의 AI만 API 요청 보내기
                    for (type in types) {

                        // 무슨 케이스로 랜덤된건지 확인하는 로`
                        Log.d("랜덤케이스 걸린거 확인용 로그", """
                            AI 종: $type
                            AI끼리 응답 여부: $isChainedResponse
                            요청 보내는 내용: $previousResponse
                        """.trimIndent())

                        // 각 AI별로 로딩 메시지 생성
                        val newLoadingMessage = ChatMessage(
                            roomId = roomId,
                            content = "",
                            isAiMessage = true,
                            isLoading = true,
                            aiType = type
                        )

                        val newLoadingId = messageDao.insertMessageAndGetId(newLoadingMessage)

                        // API 호출
                        val response = aiChatService.sendChatMessage(
                            type = type,
                            request = ChatRequest(text = previousResponse)
                        )

                        // 응답 내용 보는 디버그용 로그
                        Log.d("ChatResponse", "응답 성공 여부: ${response.isSuccessful}")
                        Log.d("ChatResponse", "응답 코드: ${response.code()}")
                        Log.d("ChatResponse", "응답 메시지: ${response.message()}")
                        Log.d("ChatResponse", "응답 바디: ${response.body()}")
                        Log.d("ChatResponse", "응답 타입(룸아이디): $type")

                        // 응답 처리
                        if (response.isSuccessful) {
                            // 로딩 메시지 삭제
                            Log.d("ChatService", "로딩 메시지 삭제 시도. ID: $loadingMessageId")

                            messageDao.deleteMessageById(newLoadingId)

                            Log.d("ChatService", "로딩 메시지 삭제 완료")

                            // 응답 메시지 처리
                            response.body()?.let { body ->
                                // 모든 메시지 처리 및 저장
                                body.getMessageList().forEachIndexed { index, aiMessage ->
                                    if (aiMessage.isNotBlank()) {
                                        if (index > 0) {
                                            kotlinx.coroutines.delay(1000)
                                        }

                                        messageDao.insertMessage(
                                            ChatMessage(
                                                roomId = roomId,
                                                content = aiMessage,
                                                isAiMessage = true,
                                                aiType = type
                                            )
                                        )
                                    }
                                }

                                // AI끼리 응답일 경우, 모든 메시지를 합쳐서 다음 AI의 입력으로 저장
                                if (isChainedResponse) {
                                    previousResponse = body.getMessageList().joinToString(" ")
                                }
                            }

                            // 마지막 메시지로 ChatInfo 업데이트
                            response.body()?.getMessageList()?.lastOrNull()?.let { lastAiMessage ->
                                val displayMessage = lastAiMessage.replace(Regex("<br\\s*/*>|<br"), " ")
                                chatInfoDao.updateLastMessage(
                                    chatId = roomId,
                                    message = displayMessage,
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                        }

                        // AI 간 응답 사이에 약간의 딜레이
                        kotlinx.coroutines.delay(700)
                    }
                } else {
                    // 디버그용 로그
                    Log.d("ChatService", "받은 loadingMessageId: $loadingMessageId")
                    Log.d("roomId","방 아이디 roomId@@@@@@@@@ : $roomId")
                    Log.d("content","내용 content@@@@@@@@@ : $content")

                    // 1:1 채팅방인 경우 로딩 메시지 생성 추가
                    val loadingMessage = ChatMessage(
                        roomId = roomId,
                        content = "",
                        isAiMessage = true,
                        isLoading = true
                    )

                    // 챗API에 채팅 메세지 전송
                    val response = aiChatService.sendChatMessage(
                        type = roomId,
                        request = ChatRequest(text = content)
                    )

                    // 응답 내용 보는 디버그용 로그
                    Log.d("ChatResponse", "응답 성공 여부: ${response.isSuccessful}")
                    Log.d("ChatResponse", "응답 코드: ${response.code()}")
                    Log.d("ChatResponse", "응답 메시지: ${response.message()}")
                    Log.d("ChatResponse", "응답 바디: ${response.body()}")

                    if (response.isSuccessful) {
                        // 로딩 메시지 삭제 시도 로그
                        Log.d("ChatService", "로딩 메시지 삭제 시도. ID: $loadingMessageId")

                        // 로딩 메세지 삭제
                        loadingMessageId?.let { id ->
                            messageDao.deleteMessageById(id)
                            Log.d("ChatService", "로딩 메시지 삭제 완료")
                        }

                        // 챗API 응답 메세지들을 순서대로 처리하기
                        response.body()?.getMessageList()?.forEachIndexed { index, aiMessage ->
                            // 빈 문자열이면 말풍선 안생기게 if문으로 조건 걸기
                            // 근데 이거 효과 없어서 ChatBubble(MessageBubble)에 if문 조건 달아서 해결
                            if (aiMessage.isNotBlank()) {
                                // 첫 번째 메시지가 아닌 경우 1초 대기
                                if (index > 0) {
                                    kotlinx.coroutines.delay(1000)
                                }

                                // RoomDB에 메세지 저장
                                messageDao.insertMessage(
                                    ChatMessage(
                                        roomId = roomId,
                                        content = aiMessage,
                                        isAiMessage = true
                                    )
                                )
                            }
                        }

                        // 챗API 응답으로 온 마지막 AI 메세지인 경우 ChatInfo 업데이트
                        response.body()?.getMessageList()?.lastOrNull()?.let { lastAiMessage ->
                            val displayMessage = lastAiMessage.replace(Regex("<br\\s*/*>|<br"), " ")
                            chatInfoDao.updateLastMessage(
                                chatId = roomId,
                                message = displayMessage,
                                timestamp = System.currentTimeMillis()
                            )

                            // 현재 사용자 화면이 해당 채팅방이 아니라면 읽지 않은 메세지 수 증가시키기
                            if (activeChatRoomId != roomId) {
                                val currentChat = chatInfoDao.getChatById(roomId)
                                currentChat?.let {
                                    chatInfoDao.updateUnreadCount(
                                        chatId = roomId,
                                        count = it.unreadCount + 1
                                    )
                                }
                            }
                        }
                    } else {
                        // API 응답 실패 하면 나오는 에러
                        loadingMessageId?.let { id ->
                            messageDao.deleteMessageById(id)
                        }
                        handleError(roomId, "죄송해요, 메시지를 받지 못했어요.")
                    }
                }
            } catch (e: Exception) {
                // 인터넷 연결 등 오류 발생하면 나오는 에러
                loadingMessageId?.let { id ->
                    messageDao.deleteMessageById(id)
                }
                handleError(roomId, "네트워크 오류가 발생했어요. 인터넷 연결을 확인해주세요.")
            }
        }
    }

    // 채팅 중 오류 발생 시 처리하는 함수
    private suspend fun handleError(roomId: Int, errorMessage: String) {
        // 오류 메시지를 AI 메시지로 채팅 RoomDB에 저장
        messageDao.insertMessage(
            ChatMessage(
                roomId = roomId,
                content = errorMessage,
                isAiMessage = true
            )
        )

        // 채팅목록 페이지 마지막 메시지를 오류 메시지로 수정
        chatInfoDao.updateLastMessage(
            chatId = roomId,
            message = errorMessage,
            timestamp = System.currentTimeMillis()
        )

        // 현재 사용자 화면에 있는 채팅방이 아니면 읽지 않은 메시지 수 증가
        if (activeChatRoomId != roomId) {
            val currentChat = chatInfoDao.getChatById(roomId)
            currentChat?.let {
                chatInfoDao.updateUnreadCount(
                    chatId = roomId,
                    count = it.unreadCount + 1
                )
            }
        }
    }

    // 알림 채널을 생성하는 함수
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Silent Channel",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = ""
                setShowBadge(false)
                enableLights(false) // LED 끄기
                enableVibration(false) // 진동 끄기
                setSound(null, null) // 알림음 없애기
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 알림을 생성하는 함수
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("")
        .setContentText("")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    // Started Service로 사용할거라서 onBind는 필요없기 때문에 null
    override fun onBind(intent: Intent?): IBinder? = null

    // 서비스 종료시 리소스 정리하기 (메모리 누수 방지, 리소스 낭비 방지)
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "chat_service_channel"   // 알림 채널 ID
        private const val NOTIFICATION_ID = 1                   // 알림 고유 ID
        const val ACTION_START_CHAT = "action_start_chat"       // 채팅 시작 액션
        const val EXTRA_ROOM_ID = "extra_room_id"               // 채팅방 ID 키
        const val EXTRA_CONTENT = "extra_content"               // 채팅 내용 키
        const val EXTRA_LOADING_MESSAGE_ID = "extra_loading_message_id"     // 로딩 메시지 ID 키
        private var activeChatRoomId: Int? = null  // 현재 사용자 휴대폰 화면이 몇번 채팅방인지 확인하는 변수
        private var nextResponderId: Int? = null  // 다음 응답자 ID를 저장

        fun getNextResponderId(): Int? {
            return nextResponderId
        }

        fun setNextResponderId(id: Int?) {
            nextResponderId = id
        }

        // 채팅 서비스 시작하는 함수
        fun startService(context: Context, roomId: Int, content: String, loadingMessageId: Long?) {
            val intent = Intent(context, ChatService::class.java).apply {
                action = ACTION_START_CHAT
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_CONTENT, content)
                loadingMessageId?.let {
                    putExtra(EXTRA_LOADING_MESSAGE_ID, it)
                }
            }
            context.startForegroundService(intent)
        }

        // 현재 화면이 몇번 채팅방인지 채팅방 ID 설정 하기 위한 함수
        fun setActiveChatRoom(roomId: Int?) {
            activeChatRoomId = roomId
        }

        // 현재 활성화된 채팅방 ID를 반환하는 함수
        // 읽지 않은 메시지 카운트 업데이트 시 사용
        fun getActiveChatRoom(): Int? {
            return activeChatRoomId
        }
    }
}