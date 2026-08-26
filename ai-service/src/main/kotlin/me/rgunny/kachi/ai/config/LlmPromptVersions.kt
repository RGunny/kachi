package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.domain.llm.PromptVersion

/**
 * 유스케이스별 prompt version을 한곳에서 들고 다니는 값 묶음.
 *
 * 저장 키의 일부라 provider 생성과 선조회가 같은 값을 봐야 한다.
 * [PromptVersion] 자체를 빈으로 두면 같은 타입이 둘이라 qualifier가 필요해지므로 묶어서 빈으로 만든다.
 */
data class LlmPromptVersions(
    val keywordExpansion: PromptVersion,
    val newsSummary: PromptVersion
)
