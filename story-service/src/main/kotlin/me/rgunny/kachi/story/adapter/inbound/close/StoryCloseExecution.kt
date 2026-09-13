package me.rgunny.kachi.story.adapter.inbound.close

/**
 * 닫기 실행 요청의 결과.
 *
 * 다른 실행이 lock을 쥐고 있어 건너뛴 것은 정상이고, lock을 확인할 수 없어 시작하지 못한 것은 장애라 둘을 나눈다.
 */
sealed interface StoryCloseExecution
