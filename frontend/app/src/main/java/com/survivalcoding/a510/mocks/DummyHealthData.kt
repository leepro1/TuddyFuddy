package com.survivalcoding.a510.mocks

data class HealthData(
    val heartRate: Int,
    val steps: Int,
    val sleepMinutes: Int,
    val stressLevel: Int
)

object DummyHealthData {
    val healthList = listOf(
//        HealthData(                 // 잠 잘 못잔 케이스
//            heartRate = 70,         // 심박수 : 100 초과 혹은 60 미만이면 발동
//            steps = 9000,           // 걸음수 : 1만보 초과하면 발동
//            sleepMinutes = 299,     // 수면시간 : 300(5시간) 미만면 발동
//            stressLevel = 50,       // 스트레스 : 1~100이라 50 초과하면 발동
//        ),
        HealthData(                 // 심박수 100 이상인 케이스
            heartRate = 101,        // 심박수 : 100 초과 혹은 60 미만이면 발동
            steps = 9000,           // 걸음수 : 1만보 초과하면 발동
            sleepMinutes = 300,     // 수면시간 : 300(5시간) 미만면 발동
            stressLevel = 50,       // 스트레스 : 1~100이라 50 초과하면 발동
        ),
//        HealthData(                 // 심박수 60 미만인 케이스
//            heartRate = 55,         // 심박수 : 100 초과 혹은 60 미만이면 발동
//            steps = 9000,           // 걸음수 : 1만보 초과하면 발동
//            sleepMinutes = 300,     // 수면시간 : 300(5시간) 미만면 발동
//            stressLevel = 50,       // 스트레스 : 1~100이라 50 초과하면 발동
//        ),
//        HealthData(                 // 스트레스 높은 케이스
//            heartRate = 70,         // 심박수 : 100 초과 혹은 60 미만이면 발동
//            steps = 9000,           // 걸음수 : 1만보 초과하면 발동
//            sleepMinutes = 300,     // 수면시간 : 300(5시간) 미만면 발동
//            stressLevel = 55,       // 스트레스 : 1~100이라 50 초과하면 발동
//        ),
//        HealthData(                 // 걸음수 1만보 초과한 케이스
//            heartRate = 70,         // 심박수 : 100 초과 혹은 60 미만이면 발동
//            steps = 10500,          // 걸음수 : 1만보 초과하면 발동
//            sleepMinutes = 300,     // 수면시간 : 300(5시간) 미만면 발동
//            stressLevel = 50,       // 스트레스 : 1~100이라 50 초과하면 발동
//        ),

    )
}