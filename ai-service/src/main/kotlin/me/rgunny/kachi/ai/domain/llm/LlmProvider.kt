package me.rgunny.kachi.ai.domain.llm

/**
 * 한 계정으로 부르는 LLM 서비스 하나. 회사와 계정이다.
 *
 * 상수명은 회사명이다. 같은 회사에 계정이 둘이면 `<회사>_<계정 보유자>`, 로컬 서버가 둘이면 `<회사>_<호스트>`로 늘린다.
 * 과금 상태 같은 것은 이름에 넣지 않는다. 그것은 설정의 몫이다.
 *
 * [code]는 영속 문서·이벤트 계약·서킷 이름에 쓰는 값이라 상수명과 별개로 고정한다.
 */
enum class LlmProvider(
    val code: String,
    val api: LlmApi
) {
    GROQ("groq", LlmApi.OPENAI_CHAT_COMPLETIONS),
    MISTRAL("mistral", LlmApi.OPENAI_CHAT_COMPLETIONS),
    OPENROUTER("openrouter", LlmApi.OPENAI_CHAT_COMPLETIONS),
    TOGETHER("together", LlmApi.OPENAI_CHAT_COMPLETIONS),
    CEREBRAS("cerebras", LlmApi.OPENAI_CHAT_COMPLETIONS),
    OLLAMA("ollama", LlmApi.OPENAI_CHAT_COMPLETIONS),
    MOONSHOT("moonshot", LlmApi.OPENAI_CHAT_COMPLETIONS);

    companion object {

        /**
         * 저장된 code에서 제공자를 되찾는다. 모르는 code는 저장 당시의 상수가 지워진 것이므로 실패시킨다.
         */
        fun ofCode(code: String): LlmProvider {
            return entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("알 수 없는 LLM provider code입니다: $code")
        }
    }
}
