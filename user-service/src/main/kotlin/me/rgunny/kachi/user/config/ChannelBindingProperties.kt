package me.rgunny.kachi.user.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 채널 바인딩 설정. 주소 암호화 키와 연결 토큰 만료 시간.
 */
@ConfigurationProperties(prefix = "kachi.user.binding")
data class ChannelBindingProperties(
    /** base64로 표기한 32바이트 AES 키. */
    val encryptionKey: String,
    val linkTokenTtl: Duration
)
