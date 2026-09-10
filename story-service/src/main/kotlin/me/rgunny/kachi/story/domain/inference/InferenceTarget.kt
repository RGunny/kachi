package me.rgunny.kachi.story.domain.inference

/**
 * 추론 서버가 맡는 역할.
 */
enum class InferenceTarget {
    /** 텍스트를 벡터로 바꾸는 임베딩 서버. */
    EMBEDDING,

    /** 기사 쌍이 같은 사건인지 채점하는 판정기 서버. */
    JUDGE
}
