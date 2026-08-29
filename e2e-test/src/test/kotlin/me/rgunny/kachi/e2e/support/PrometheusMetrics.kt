package me.rgunny.kachi.e2e.support

/**
 * 서비스의 `/actuator/prometheus` 노출 텍스트에서 카운터 값을 읽는다.
 *
 * 카운터는 컨테이너가 사는 동안 누적되므로, 한 시나리오의 효과는 전후 차이로 본다.
 */
class PrometheusMetrics(private val http: JsonHttp, private val baseUrl: String) {

    /** 이름이 같고 [tags]를 전부 포함하는 시계열의 값을 더한다. */
    fun counter(name: String, tags: Map<String, String>): Double {
        return http.getText("$baseUrl/actuator/prometheus").lineSequence()
            .filter { it.startsWith("$name{") || it.startsWith("$name ") }
            .filter { line -> tags.all { (key, value) -> line.contains("$key=\"$value\"") } }
            .sumOf { it.substringAfterLast(' ').toDouble() }
    }

    companion object {
        const val RECIPIENT_RESOLVE_TOTAL = "kachi_notification_recipient_resolve_total"
    }
}
