package me.rgunny.kachi.ai.domain.llm

/**
 * 한 제공자에서 부르는 모델 하나.
 *
 * 상수는 실호출 검증을 통과한 것만 둔다. 제공이 끝난 모델은 상수를 지운다.
 * 상수명은 `<PROVIDER>_<모델 짧은 이름>`이고, [code]는 요청에 싣는 wire id다.
 * `-latest` 같은 이동 alias는 쓰지 않는다. 어느 날 다른 모델이 응답해도 알 길이 없다.
 * Ollama tag는 이동 alias지만 digest 고정 호출이 확인되지 않아 예외로 두고, 실호출 검증이 digest를 남긴다.
 */
enum class LlmModel(
    val provider: LlmProvider,
    val code: String,
    val options: LlmRequestOptions
) {
    GROQ_QWEN3_27B(
        provider = LlmProvider.GROQ,
        code = "qwen/qwen3.8-27b",
        options = LlmRequestOptions(reasoningEffort = ReasoningEffort.OMIT, thinking = Thinking.OMIT)
    ),
    MISTRAL_SMALL_2603(
        provider = LlmProvider.MISTRAL,
        code = "mistral-small-2603",
        options = LlmRequestOptions(reasoningEffort = ReasoningEffort.OMIT, thinking = Thinking.OMIT)
    ),
    OLLAMA_QWEN3_27B(
        provider = LlmProvider.OLLAMA,
        code = "qwen3.8:27b",
        options = LlmRequestOptions(reasoningEffort = ReasoningEffort.NONE, thinking = Thinking.OMIT)
    );

    /**
     * 제공자와 모델을 함께 적은 이름. 서킷 이름과 로그에 쓴다. 같은 wire id를 여러 제공자가 낼 수 있어 code만으로는 모자란다.
     */
    val qualifiedCode: String
        get() = "${provider.code}/$code"
}
