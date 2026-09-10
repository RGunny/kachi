package me.rgunny.kachi.story.application.port.outbound.lock.model

/**
 * story-service가 lock으로 보호하는 실행 단위.
 */
enum class StoryExecutionLock(
    override val key: String,
    override val scope: ExecutionLockScope
) : ExecutionLockTarget {

    /**
     * 오래 조용한 story를 닫고 색인에서 뺀다.
     */
    STORY_CLOSE("story-service:story-close", ExecutionLockScope.CLUSTER),

    /**
     * 가까운 story 쌍을 합친다.
     */
    STORY_MERGE("story-service:story-merge", ExecutionLockScope.CLUSTER),

    /** 보관 창을 지난 벡터를 색인에서 지운다. */
    INDEX_CLEANUP("story-service:index-cleanup", ExecutionLockScope.CLUSTER),

    /** 저장된 임베딩으로 색인을 다시 만든다. */
    INDEX_REBUILD("story-service:index-rebuild", ExecutionLockScope.CLUSTER),

    /**
     * outbox relay tick. 행 단위 중복은 저장소의 조건부 쓰기가 막는다.
     */
    OUTBOX_RELAY("story-service:outbox-relay", ExecutionLockScope.INSTANCE)
}
