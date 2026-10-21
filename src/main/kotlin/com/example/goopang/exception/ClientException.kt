package com.example.goopang.exception

// 메시지를 클라이언트에게 그대로 전달하는 예외 (조회 대상 없음, 입력값 오류 등)
class ClientException(message: String) : RuntimeException(message)
