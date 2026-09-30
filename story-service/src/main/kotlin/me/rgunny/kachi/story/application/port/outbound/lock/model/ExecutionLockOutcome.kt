package me.rgunny.kachi.story.application.port.outbound.lock.model

/**
 * 실행 lock을 거친 요청의 결과.
 *
 * 다른 실행이 쥐고 있어 막힌 것은 정상이고 다음 차례에 풀리지만, lock을 판단할 수 없어 막힌 것은 장애라 둘을 나눈다.
 */
sealed interface ExecutionLockOutcome<out T>
