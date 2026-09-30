package me.rgunny.kachi.story.adapter.outbound.tei

import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiInfoResponse
import me.rgunny.kachi.story.domain.inference.InferenceTarget

/**
 * 기동 시 추론 서버에 기대한 모델이 떠 있는지 `/info`로 확인하는 probe.
 *
 * 서버에 닿지 않거나 정체가 다르면 예외를 던져 기동을 실패시킨다.
 * 임베딩 서버는 `embedding` 종류에 pooling `cls`, judge 서버는 `reranker` 종류여야 한다.
 */
class TeiStartupProbe(
    private val client: TeiClient,
    private val target: InferenceTarget,
    private val expectedModelId: String
) {

    /** 통과하면 서버가 보고한 정보를 돌려준다. */
    suspend fun verify(): TeiInfoResponse {
        val info = client.info()

        check(info.model_id == expectedModelId) {
            "TEI $target 서버의 모델이 다릅니다: expected=$expectedModelId, actual=${info.model_id}"
        }
        when (target) {
            InferenceTarget.EMBEDDING -> {
                val embedding = checkNotNull(info.model_type?.embedding) {
                    "TEI $target 서버가 임베딩 모델이 아닙니다: model_type=${info.model_type}"
                }
                check(embedding.pooling == EXPECTED_POOLING) {
                    "TEI $target 서버의 pooling이 다릅니다: expected=$EXPECTED_POOLING, actual=${embedding.pooling}"
                }
            }

            InferenceTarget.JUDGE -> checkNotNull(info.model_type?.reranker) {
                "TEI $target 서버가 judge 모델이 아닙니다: model_type=${info.model_type}"
            }
        }

        return info
    }

    companion object {
        const val EXPECTED_POOLING = "cls"
    }
}
