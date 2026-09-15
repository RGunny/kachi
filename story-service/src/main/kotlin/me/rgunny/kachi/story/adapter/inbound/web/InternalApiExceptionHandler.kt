package me.rgunny.kachi.story.adapter.inbound.web

import me.rgunny.kachi.story.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.story.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.story.application.exception.StoryErrorCode
import me.rgunny.kachi.story.application.exception.StoryException
import me.rgunny.kachi.story.application.exception.StoryOperationErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebInputException

/**
 * 내부 API가 던진 예외를 `ApiResponse` 오류 응답으로 옮기는 예외 핸들러.
 *
 * application 예외는 전부 [StoryException]이고 [StoryErrorCode]를 가지므로 타입이 아니라 코드로 분기한다.
 * HTTP status는 application이 아니라 web [ErrorCode]가 갖는다.
 */
@RestControllerAdvice
class InternalApiExceptionHandler {

    @ExceptionHandler(StoryException::class)
    fun handleStoryException(exception: StoryException): ResponseEntity<ApiResponse<Unit>> {
        val errorCode = ERROR_CODES[exception.errorCode]

        // 매핑이 없는 실패는 응답 규약을 정하지 않은 경로다. 200으로 새지 않게 500으로 막고 원인을 남긴다.
        if (errorCode == null) {
            log.error("Unmapped internal API failure: code={}", exception.errorCode.code, exception)
            return failure(ErrorCode.INTERNAL_ERROR, message = null)
        }

        return failure(errorCode, exception.message)
    }

    /**
     * enum/UUID처럼 타입이 맞지 않는 파라미터는 WebFlux가 자체 오류 body로 응답한다.
     * 내부 API 응답은 성공/실패가 같은 형식이어야 하므로 여기서 잡아 바꾼다.
     */
    @ExceptionHandler(ServerWebInputException::class)
    fun handleInvalidInput(exception: ServerWebInputException): ResponseEntity<ApiResponse<Unit>> {
        return failure(ErrorCode.INVALID_INTERNAL_REQUEST, exception.reason)
    }

    private fun failure(errorCode: ErrorCode, message: String?): ResponseEntity<ApiResponse<Unit>> {
        return ResponseEntity.status(errorCode.status)
            .body(ApiResponse.failure(errorCode, message))
    }

    private companion object {
        val log = LoggerFactory.getLogger(InternalApiExceptionHandler::class.java)

        val ERROR_CODES: Map<StoryErrorCode, ErrorCode> = mapOf(
            StoryOperationErrorCode.STORY_NOT_FOUND to ErrorCode.STORY_NOT_FOUND,
            StoryOperationErrorCode.STORY_NOT_OPEN to ErrorCode.STORY_NOT_OPEN,
            StoryOperationErrorCode.MERGE_INCOMPATIBLE to ErrorCode.STORY_MERGE_INCOMPATIBLE,
            StoryOperationErrorCode.SPLIT_ARTICLES_REQUIRED to ErrorCode.INVALID_STORY_SPLIT,
            StoryOperationErrorCode.SPLIT_ARTICLES_NOT_IN_STORY to ErrorCode.INVALID_STORY_SPLIT,
            StoryOperationErrorCode.SPLIT_ALL_ARTICLES_REJECTED to ErrorCode.INVALID_STORY_SPLIT,
            StoryOperationErrorCode.REORGANIZE_CONFLICT to ErrorCode.STORY_CHANGED
        )
    }
}
