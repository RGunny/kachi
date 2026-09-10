package me.rgunny.kachi.story.config

import java.time.Duration
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.StoryJudge
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 추론 서버 호출 설정.
 */
@ConfigurationProperties(prefix = InferenceProperties.PREFIX)
data class InferenceProperties(
    val embedding: EmbeddingProperties,
    val judge: JudgeProperties,
    val circuitBreaker: InferenceCircuitBreakerProperties
) {
    companion object {
        const val PREFIX = "kachi.story.inference"
    }

    /**
     * 임베딩 서버.
     *
     * 항상 있어야 한다.
     */
    data class EmbeddingProperties(
        val model: EmbeddingModel,
        val baseUrl: String,
        val connectTimeout: Duration,
        val timeout: Duration,
        val slowAfter: Duration,
        val batchSize: Int
    ) {
        init {
            require(batchSize >= 1) { "임베딩 batch-size는 1 이상이어야 합니다" }
        }

        val server: TeiServerProperties = TeiServerProperties(
            baseUrl = baseUrl,
            connectTimeout = connectTimeout,
            timeout = timeout,
            slowAfter = slowAfter
        )
    }

    /**
     * 판정기.
     *
     * [kind]가 원격 판정기면 서버 값이 있어야 하고, 코사인만 쓰는 판정기면 서버 값이 없어도 된다.
     */
    data class JudgeProperties(
        val kind: StoryJudge,
        val baseUrl: String? = null,
        val connectTimeout: Duration? = null,
        val timeout: Duration? = null,
        val slowAfter: Duration? = null
    ) {
        /**
         * 원격 판정기의 서버 값.
         *
         * 코사인만 쓰는 판정기는 null이다.
         */
        val server: TeiServerProperties? = when (kind) {
            StoryJudge.BGE_RERANKER_V2_M3 -> TeiServerProperties(
                baseUrl = requireNotNull(baseUrl) { "판정기 ${kind.name}의 base-url이 없습니다" },
                connectTimeout = requireNotNull(connectTimeout) { "판정기 ${kind.name}의 connect-timeout이 없습니다" },
                timeout = requireNotNull(timeout) { "판정기 ${kind.name}의 timeout이 없습니다" },
                slowAfter = requireNotNull(slowAfter) { "판정기 ${kind.name}의 slow-after가 없습니다" }
            )

            StoryJudge.THRESHOLD_ONLY -> null
        }
    }
}
