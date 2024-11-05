package com.survivalcoding.a510.services

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// 인증 관련 API 요청을 정의하는 인터페이스
interface AuthService {
    @POST("auth-service/auth/kakao")
    suspend fun authenticateKakao(
        @Body request: KakaoAuthRequest
    ): Response<AuthResponse>

    // 토큰 갱신을 위한 엔드포인트
    @POST("auth-service/auth/refresh")
    suspend fun refreshToken(): Response<AuthResponse>
}

// 카카오 인증 요청 데이터
data class KakaoAuthRequest(
    val accessToken: String  // 카카오에서 받은 액세스 토큰
)

// 서버 인증 응답 데이터
data class AuthResponse(
    val token: String,   // 서버에서 발급한 액세스 토큰
    val userId: String,
    val nickname: String
)

