package me.rgunny.kachi.collector.domain

import java.net.URI
import java.security.MessageDigest

/**
 * 수집 기사의 URL.
 *
 * `value`는 provider가 준 원문 그대로이고, `hash`는 정규화한 URL의 SHA-256이다.
 * 같은 페이지를 가리키는 변형(대소문자, 기본 포트, fragment, 추적 파라미터, 파라미터 순서, 끝 슬래시, AMP)은 같은 hash를 낸다.
 * 정규화 규칙은 이 클래스 안에만 있고, 저장소는 결과값만 갖는다.
 */
@JvmInline
value class NewsUrl private constructor(
    val value: String
) {
    /**
     * 같은 페이지의 URL 변형을 하나로 모은 값. hash의 입력이다.
     */
    val canonical: String
        get() = canonicalize(value)

    val hash: String
        get() = sha256(canonical.toByteArray(Charsets.UTF_8))

    companion object {

        fun of(value: String): NewsUrl {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "뉴스 URL은 빈 값일 수 없습니다" }

            return NewsUrl(normalized)
        }

        private val TRACKING_PARAMETER_PREFIXES = listOf("utm_")
        private val TRACKING_PARAMETERS = setOf(
            "fbclid", "gclid", "dclid", "yclid", "msclkid", "igshid",
            "mc_cid", "mc_eid", "ref", "ref_src", "_ga", "_gl"
        )
        private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)
        private const val AMP_SUBDOMAIN = "amp."
        private val AMP_PATH_SUFFIX = Regex("/amp/?$", RegexOption.IGNORE_CASE)

        /**
         * 파싱할 수 없는 URL은 손대지 않는다. 그런 URL끼리는 원문이 같을 때만 같은 hash가 된다.
         */
        private fun canonicalize(raw: String): String {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return raw
            val scheme = uri.scheme?.lowercase() ?: return raw
            val host = uri.host?.lowercase() ?: return raw

            val canonicalHost = stripAmpSubdomain(host)
            val port = uri.port.takeUnless { it == -1 || it == DEFAULT_PORTS[scheme] }
            val path = canonicalPath(uri.rawPath.orEmpty())
            val query = canonicalQuery(uri.rawQuery)

            return buildString {
                append(scheme).append("://").append(canonicalHost)
                if (port != null) append(':').append(port)
                append(path)
                if (query.isNotEmpty()) append('?').append(query)
            }
        }

        private fun stripAmpSubdomain(host: String): String {
            if (!host.startsWith(AMP_SUBDOMAIN)) return host

            val stripped = host.removePrefix(AMP_SUBDOMAIN)
            // "amp.com" 같은 두 레이블 호스트는 서브도메인이 아니다.
            return if (stripped.count { it == '.' } >= 1) stripped else host
        }

        private fun canonicalPath(rawPath: String): String {
            val withoutAmp = AMP_PATH_SUFFIX.replace(rawPath, "")
            val withoutTrailingSlash = withoutAmp.trimEnd('/')
            return withoutTrailingSlash
        }

        private fun canonicalQuery(rawQuery: String?): String {
            if (rawQuery.isNullOrEmpty()) return ""

            return rawQuery.split('&')
                .filter { it.isNotEmpty() }
                .map { parameter -> parameter.substringBefore('=') to parameter }
                .filterNot { (key, _) -> isTrackingParameter(key.lowercase()) }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .joinToString("&") { it.second }
        }

        private fun isTrackingParameter(key: String): Boolean =
            key in TRACKING_PARAMETERS || TRACKING_PARAMETER_PREFIXES.any { key.startsWith(it) }

        private fun sha256(input: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input)
                .joinToString("") { "%02x".format(it) }
    }
}
