package me.rgunny.kachi.notification.routing.adapter.monitoring

/**
 * notification-routing이 발행하는 metric의 안정적인 운영 계약.
 *
 * Prometheus/Grafana가 소비하는 이름이므로 배포 설정이나 topic 이름과 독립적으로 유지한다.
 */
object NotificationRoutingMetricContract {

    object Names {
        const val ROUTING = "kachi.notification.routing"
        const val ROUTING_DURATION = "kachi.notification.routing.duration"
    }

    /** 시계열 수를 예측할 수 있도록 허용된 저카디널리티 tag key만 선언한다. */
    object Tags {
        const val KIND = "kind"
        const val RESULT = "result"
    }

    /** ai 이벤트 1건의 라우팅 결과. routed는 대상 0건인 완료도 포함한다. */
    object Results {
        const val ROUTED = "routed"
        const val SKIPPED = "skipped"
        const val INVALID = "invalid"
        const val FAILED = "failed"
    }
}
