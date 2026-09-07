package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.domain.llm.LlmBilling
import org.junit.jupiter.api.DisplayName

/**
 * 호출량만큼 과금되는 후보의 실호출 검증. 같은 본문이지만 클래스를 나눠 실행 자체가 opt-in이 되게 한다.
 */
@DisplayName("LLM 과금 후보 실호출 검증")
class LlmMeteredVerificationTest : LlmVerification(includes = { billing -> billing == LlmBilling.METERED })
