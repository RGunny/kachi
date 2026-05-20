package me.rgunny.kachi.user.application.exception

class InvalidTokenException(
    message: String = "유효하지 않은 토큰입니다"
) : RuntimeException(message)
