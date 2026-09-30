package me.rgunny.kachi.ai.application.exception

/**
 * ai-service application 예외의 최상위 타입.
 *
 * application 계층에서 던지는 예외는 모두 이 타입을 상속해 [errorCode]를 갖는다.
 * 예외를 다루는 지점(실행 기록, internal API 오류 응답)이 단일 규약 관리하기 위함이다.
 */
abstract class AiException(
    val errorCode: AiErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {

        /**
         * 에러 코드와 선택적 상세를 외부 노출 메시지 포맷으로 조립한다.
         *
         * 상세는 status나 응답 body처럼 호출 시점에만 알 수 있는 값이라 코드에 담을 수 없다.
         */
        fun messageOf(errorCode: AiErrorCode, detail: String?): String {
            return detail
                ?.takeIf { it.isNotBlank() }
                ?.let { "${errorCode.code} ${errorCode.message}: $it" }
                ?: "${errorCode.code} ${errorCode.message}"
        }
    }
}
