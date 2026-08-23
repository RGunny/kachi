package me.rgunny.kachi.ai.adapter.inbound.keyword

/**
 * 키워드 확장 요청의 처리 상태 결과.
 *
 * 두 결과는 담는 값도 호출자의 처리도 다르다. 실행된 요청은 실행 결과를, 막힌 요청은
 * 이미 실행 중인 작업의 시작 시각을 갖는다. sealed로 경우의 수를 닫아
 * 진입점이 "요청이 막혔을 수 있다"는 경우를 빠뜨릴 수 없게 한다.
 *
 * 컴파일 강제 지점은 두 곳이다.
 * - `InternalAiKeywordExpansionController.expandKeywords`의 when: 200과 409 응답 분기
 * - `AiKeywordExpansionScheduler`의 when: 실행 로그와 skip 로그 분기
 *
 * 결과가 늘어나면 두 진입점이 함께 컴파일되지 않으므로, 한쪽만 고쳐 규칙이 갈라질 수 없다.
 */
sealed interface AiKeywordExpansionExecutionResult
