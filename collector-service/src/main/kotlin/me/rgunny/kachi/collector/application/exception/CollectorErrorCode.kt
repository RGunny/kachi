package me.rgunny.kachi.collector.application.exception

/**
 * collector-service application 예외가 노출하는 에러 코드 규약.
 *
 * 예외를 잡는 쪽이 타입 분기 없이 코드 하나로 응답과 로그를 만들 수 있게 한다.
 */
interface CollectorErrorCode {
    val code: String
    val message: String
}
