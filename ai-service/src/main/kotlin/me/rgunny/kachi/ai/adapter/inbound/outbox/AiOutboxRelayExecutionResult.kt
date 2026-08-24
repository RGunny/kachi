package me.rgunny.kachi.ai.adapter.inbound.outbox

/**
 * relay 요청의 처리 상태 결과.
 *
 * 실행된 요청은 tick 집계를, 막힌 요청은 이미 실행 중인 tick의 시작 시각을 갖는다.
 * sealed로 경우의 수를 닫아 진입점이 "요청이 막혔을 수 있다"는 경우를 빠뜨릴 수 없게 한다.
 */
sealed interface AiOutboxRelayExecutionResult
