package me.rgunny.kachi.story.application.exception

/**
 * story-service application 예외의 최상위 타입.
 */
abstract class StoryException(
    val errorCode: StoryErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {

        /**
         * 오류 코드와 선택적 상세를 외부 노출 메시지로 조립한다.
         */
        fun messageOf(errorCode: StoryErrorCode, detail: String?): String {
            return detail
                ?.takeIf { it.isNotBlank() }
                ?.let { "${errorCode.code} ${errorCode.message}: $it" }
                ?: "${errorCode.code} ${errorCode.message}"
        }
    }
}
