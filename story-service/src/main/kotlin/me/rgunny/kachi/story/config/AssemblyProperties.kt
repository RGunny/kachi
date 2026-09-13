package me.rgunny.kachi.story.config

import java.time.Duration
import me.rgunny.kachi.story.application.service.assembly.AssemblyPolicy
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 조립 임계값과 한도 설정.
 *
 * 검증은 [AssemblyPolicy]가 한다.
 */
@ConfigurationProperties(prefix = AssemblyProperties.PREFIX)
data class AssemblyProperties(
    val thetaHigh: Double,
    val thetaLow: Double,
    val thetaJudge: Double,
    val candidateLimit: Int,
    val recentArticles: Int,
    val candidateWindow: Duration,
    val maxArticles: Int,
    val maxCasRetries: Int
) {
    companion object {
        const val PREFIX = "kachi.story.assembly"
    }

    fun toPolicy(): AssemblyPolicy {
        return AssemblyPolicy(
            thetaHigh = thetaHigh,
            thetaLow = thetaLow,
            thetaJudge = thetaJudge,
            candidateLimit = candidateLimit,
            recentArticles = recentArticles,
            candidateWindow = candidateWindow,
            maxArticles = maxArticles,
            maxCasRetries = maxCasRetries
        )
    }
}
