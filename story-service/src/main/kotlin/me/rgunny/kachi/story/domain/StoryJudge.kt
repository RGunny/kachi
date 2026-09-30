package me.rgunny.kachi.story.domain

/**
 * 회색 구간의 기사 쌍이 같은 사건인지 채점하는 judge.
 */
enum class StoryJudge(
    val code: String
) {
    /** cross-encoder. */
    BGE_RERANKER_V2_M3("bge-reranker-v2-m3"),

    /** judge 없이 코사인 유사도를 그대로 점수로 쓴다. */
    THRESHOLD_ONLY("threshold-only");

    companion object {

        /** 저장된 code에서 judge를 되찾는다. */
        fun ofCode(code: String): StoryJudge {
            return entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("알 수 없는 judge code입니다: $code")
        }
    }
}
