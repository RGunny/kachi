package me.rgunny.kachi.notification.service.adapter.monitoring

/**
 * notification-service가 발행하는 metric의 안정적인 운영 계약.
 *
 * Spring configuration namespace와 수명 주기가 다르므로
 * application property에서 값을 만들거나 주입하지 않는다.
 */
object NotificationServiceMetricContract {

    /**
     * Prometheus/Grafana가 소비하는 이름이므로
     * 배포 설정이나 topic 이름과 독립적으로 유지한다.
     */
    object Names {
        const val REQUEST = "kachi.notification.request"
        const val REQUEST_DURATION = "kachi.notification.request.duration"
        const val OUTBOX_PUBLISH = "kachi.notification.outbox.publish"
        const val OUTBOX_PUBLISH_DURATION = "kachi.notification.outbox.publish.duration"
    }

    /** 시계열 수를 예측할 수 있도록 허용된 저카디널리티 tag key만 선언한다. */
    object Tags {
        const val SOURCE = "source"
        const val CHANNEL = "channel"
        const val RESULT = "result"
    }

    /** payload 해석 실패처럼 tag 값을 알 수 없는 경우에도 tag schema를 일정하게 유지한다. */
    object TagValues {
        const val UNKNOWN = "UNKNOWN"
    }

    /** 같은 request metric에서 HTTP와 Kafka 인입을 구분하는 tag 값이다. */
    enum class RequestSource(val value: String) {
        HTTP("http"),
        KAFKA("kafka"),
    }

    /** recorder 외부에서 임의 문자열을 만들지 않도록 업무 결과별 허용값을 제한한다. */
    object Results {
        object Request {
            const val ACCEPTED = "accepted"
            const val DUPLICATED = "duplicated"
            const val INVALID = "invalid"
            const val FAILED = "failed"
        }

        object OutboxPublishTick {
            const val COMPLETED = "completed"
            const val FAILED = "tick_failed"
        }

        /** 한 번의 tick이 처리한 outbox 단건 수량을 결과별로 누적한다. */
        object OutboxPublish {
            const val PUBLISHED = "published"
            const val FAILED = "failed"
        }
    }
}
