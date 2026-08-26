package me.rgunny.kachi.user.application.exception

/**
 * 연결 토큰이 없거나 만료됐다. 둘을 구분하지 않아 토큰 존재 여부를 추측할 수 없게 한다.
 */
class LinkTokenInvalidException : RuntimeException("연결 토큰이 유효하지 않습니다")
