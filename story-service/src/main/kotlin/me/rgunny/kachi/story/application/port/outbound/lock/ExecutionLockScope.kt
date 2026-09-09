package me.rgunny.kachi.story.application.port.outbound.lock

/**
 * 실행 lock이 미치는 범위.
 */
enum class ExecutionLockScope {

    /** 이 인스턴스 안에서만 겹치지 않으면 되는 작업. */
    INSTANCE,

    /**
     * 어느 인스턴스에서든 한 번만 실행되어야 하는 작업.
     */
    CLUSTER
}
