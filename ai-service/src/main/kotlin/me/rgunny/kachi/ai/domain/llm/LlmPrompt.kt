package me.rgunny.kachi.ai.domain.llm

/**
 * 용도 하나의 프롬프트와 그 버전.
 *
 * 본문과 버전을 한 파일에 둔다. 생성 결과에 영향을 주는 변경이면 같은 파일에서 [version]을 올린다.
 * system 본문, user 입력의 모양, 출력 형식이 그 대상이다. 모델 교체는 대상이 아니다(ADR 030).
 * [version]은 요약·확장 저장 키의 일부라, 올리면 같은 입력도 새 프롬프트로 다시 생성되고 올리지 않으면 옛 결과를 재사용한다.
 *
 * 사용자 메시지는 자연어가 아니라 입력 데이터의 JSON이다. 외부에서 온 값(기사 제목, 키워드)이 지시로 읽히지 않도록
 * [system]이 그 값을 인용 데이터로 선언하고, 직렬화가 줄바꿈·따옴표를 이스케이프한다.
 * 입력 객체는 용도마다 달라 각 object가 만들고, API 규격별 adapter가 그것을 JSON 문자열로 옮긴다.
 */
sealed interface LlmPrompt {
    val use: LlmUse
    val version: PromptVersion
    val system: String

    /** 응답에 허용하는 출력 토큰 상한. 용도가 기대하는 응답 길이의 속성이라 모델이 아니라 프롬프트에 둔다. */
    val maxTokens: Int
}
