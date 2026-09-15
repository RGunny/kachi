package me.rgunny.kachi.story.adapter.inbound.web.response

import org.springframework.http.HttpStatus

/**
 * 내부 API 오류 응답의 코드와 HTTP status.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    STORY_NOT_FOUND(HttpStatus.NOT_FOUND, "story를 찾을 수 없습니다"),
    STORY_NOT_OPEN(HttpStatus.CONFLICT, "OPEN story만 병합·분리할 수 있습니다"),
    STORY_MERGE_INCOMPATIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "두 story를 합칠 수 없습니다"),
    INVALID_STORY_SPLIT(HttpStatus.BAD_REQUEST, "분리 요청이 올바르지 않습니다"),
    STORY_CHANGED(HttpStatus.CONFLICT, "story가 그 사이 바뀌어 재편성하지 못했습니다"),
    OUTBOX_NOT_FOUND(HttpStatus.NOT_FOUND, "outbox 행을 찾을 수 없습니다"),
    OUTBOX_NOT_RECOVERABLE(HttpStatus.CONFLICT, "DEAD 상태의 outbox만 복구할 수 있습니다"),
    INDEX_REBUILD_ALREADY_RUNNING(HttpStatus.CONFLICT, "색인 재구축이 이미 실행 중입니다"),
    INDEX_REBUILD_LOCK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "실행 lock을 확인할 수 없어 색인 재구축을 시작하지 못했습니다"),
    INVALID_INTERNAL_REQUEST(HttpStatus.BAD_REQUEST, "내부 API 요청이 올바르지 않습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다")
}
