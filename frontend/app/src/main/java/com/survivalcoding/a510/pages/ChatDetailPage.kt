package com.survivalcoding.a510.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.survivalcoding.a510.R
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.platform.LocalDensity
import com.survivalcoding.a510.components.ChatBubble
import com.survivalcoding.a510.components.TextInput



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailPage(
    navController: NavController,
    chatId: Int,
) {
    var messageText by remember { mutableStateOf("") }
    // 메시지 목록을 로컬 상태로 관리
    var messages by remember { mutableStateOf(listOf<String>()) }

    val ime = WindowInsets.ime
    val insets = WindowInsets.navigationBars
    val density = LocalDensity.current
    val isKeyboardOpen by remember {
        derivedStateOf {
            ime.getBottom(density) > insets.getBottom(density)
        }
    }

    val chatData = remember {
        when (chatId) {
            1 -> ChatData(
                id = 1,
                profileImage = R.drawable.cha,
                name = "활명수",
                message = "늦었다고 생각할 때가 진짜 늦은 거야",
                timestamp = "2분 전",
                unreadCount = 2
            )

            2 -> ChatData(
                id = 2,
                profileImage = R.drawable.back,
                name = "백지헌",
                message = "갑자기 비내리는거 같은데 ㅠㅠ",
                timestamp = "오후 2:40",
                unreadCount = 3
            )

            else -> null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 휴대폰 기본 상태바 만큼 여백
                Spacer(modifier = Modifier.height(16.dp))

                // 상단바 고정
                TopAppBar(
                    title = { Text(chatData?.name ?: "") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back")
                        }
                    },
                    actions = {
                        val onSearchClick = { /* 나중에 함수 넣기 */ }
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "검색"
                            )
                        }
                        val onMenuClick = { /* 나중에 함수 넣기 */ }
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "메뉴"
                            )
                        }
                    },
                )

                // 상단바랑 채팅화면 구분하는 구분선
                HorizontalDivider(color = Color.LightGray)

                // 채팅 넣는 화면
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(messages.reversed()) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            ChatBubble(
                                text = message,
                                modifier = Modifier.widthIn(max = 280.dp)
                            )
                        }
                    }
                }

                // 입력창
                Box(
                    modifier = Modifier
                        .padding(bottom = if (isKeyboardOpen) 0.dp else 0.dp)
                        .imePadding()
                ) {
                    TextInput(
                        value = messageText,
                        onValueChange = { messageText = it },
                        onSendClick = {
                            if (messageText.isNotBlank()) {
                                messages = messages + messageText
                                messageText = ""
                            }
                        }
                    )
                }
            }
        }
    }
}