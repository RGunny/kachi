package me.rgunny.kachi.story.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 벡터 색인 서버의 주소와 시간 설정.
 *
 * 컬렉션 이름: <collection-prefix>-<model.code>
 */
@ConfigurationProperties(prefix = CandidateIndexProperties.PREFIX)
data class CandidateIndexProperties(
    val host: String,
    val grpcPort: Int,
    val tls: Boolean,
    val timeout: Duration,
    val collectionPrefix: String
) {
    init {
        require(host.isNotBlank()) { "색인 host는 빈 값일 수 없습니다" }
        require(grpcPort in 1..65535) { "색인 grpc-port는 1~65535여야 합니다: $grpcPort" }
        require(!timeout.isZero && !timeout.isNegative) { "색인 timeout은 양수여야 합니다: $timeout" }
        require(collectionPrefix.isNotBlank()) { "색인 collection-prefix는 빈 값일 수 없습니다" }
    }

    companion object {
        const val PREFIX = "kachi.story.index"
    }

    fun collectionName(modelCode: String): String = "$collectionPrefix-$modelCode"
}
