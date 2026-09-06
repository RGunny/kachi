package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.domain.llm.LlmBilling
import org.junit.jupiter.api.DisplayName

/**
 * 과금되지 않는 후보(무료 tier·구독·self-hosted)의 실호출 검증. `./gradlew :ai-service:realTest`의 기본 대상이다.
 */
@DisplayName("LLM 후보 실호출 검증")
class LlmVerificationTest : LlmVerification(includes = { billing -> billing != LlmBilling.METERED })
