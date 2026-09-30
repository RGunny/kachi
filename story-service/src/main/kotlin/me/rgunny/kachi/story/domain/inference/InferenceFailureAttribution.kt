package me.rgunny.kachi.story.domain.inference

/** 추론 서버 호출 실패의 책임이 어디 있는가. */
enum class InferenceFailureAttribution {
    /**
     * 이 요청의 입력.
     *
     * 빈 입력, 한도를 넘는 본문, 토크나이저가 거부한 텍스트.
     */
    INPUT,

    /**
     * 떠 있는 모델.
     *
     * 차원이나 개수가 계약과 다른 응답.
     */
    MODEL,

    /**
     * 우리가 띄운 서버.
     *
     * 과부하, 추론 실패, 서버 오류, 네트워크, timeout.
     */
    SERVER,

    /** 우리 가드가 호출 전에 막았다. */
    NONE
}
