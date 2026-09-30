package me.rgunny.kachi.story.application.port.outbound.lock.model

/**
 * lock으로 보호하는 실행 단위.
 */
interface ExecutionLockTarget {

    /**
     * lock을 식별하는 값.
     *
     * `{service-name}:{work-name}`
     */
    val key: String

    val scope: ExecutionLockScope
}
