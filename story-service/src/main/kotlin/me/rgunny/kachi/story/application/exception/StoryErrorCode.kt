package me.rgunny.kachi.story.application.exception

/**
 * story-service application 예외가 노출하는 에러 코드 규약.
 */
interface StoryErrorCode {
    val code: String
    val message: String
}
