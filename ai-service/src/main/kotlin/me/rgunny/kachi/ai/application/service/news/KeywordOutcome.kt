package me.rgunny.kachi.ai.application.service.news

/**
 * 뉴스 요약 실행에서 키워드 하나가 어떻게 끝났는지.
 *
 * 세 결과는 담는 값이 서로 다르다. 성공은 요약과 생성 metadata를, 실패는 실패 원인을,
 * skip은 건너뛴 사유를 갖는다. 한 타입에 nullable 필드로 합치면
 * "성공인데 실패 원인이 있는" 조합이 타입상 표현 가능해지므로 sealed로 경우의 수를 막는다.
 *
 * 컴파일 강제 지점은 [NewsSummaryOutcome.of]의 `when`이다.
 * subject가 sealed라 분기가 빠지면 컴파일되지 않으므로, variant를 추가하면
 * 실행 기록 집계를 함께 고치지 않을 수 없다. 새 결과가 어느 카운트에도 잡히지 않은 채
 * 조용히 사라지는 일을 타입으로 막는다.
 */
internal sealed interface KeywordOutcome
