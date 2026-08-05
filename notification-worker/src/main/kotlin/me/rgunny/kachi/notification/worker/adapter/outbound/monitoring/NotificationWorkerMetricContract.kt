package me.rgunny.kachi.notification.worker.adapter.outbound.monitoring

/**
 * notification-worker가 발행하는 metric의 안정적인 운영 계약.
 *
 * Spring configuration namespace와 수명 주기가 다르므로
 * application property에서 값을 만들거나 주입하지 않는다.
 */
object NotificationWorkerMetricContract {

    /** Prometheus/Grafana가 소비하는 이름이므로 Kafka와 sender 설정값에서 조립하지 않는다. */
    object Names {
        const val DISPATCH = "kachi.notification.dispatch"
        const val DISPATCH_DURATION = "kachi.notification.dispatch.duration"
        const val DLT_PERSIST = "kachi.notification.dlt.persist"
        const val SENDER = "kachi.notification.sender"
        const val SENDER_DURATION = "kachi.notification.sender.duration"
        const val PROCESSING_RECOVERY = "kachi.notification.processing.recovery"
        const val PROCESSING_RECOVERY_DURATION = "kachi.notification.processing.recovery.duration"
    }

    /**
     * notificationId나 오류 메시지처럼 시계열을 폭증시키는 값은
     * 이 계약에 포함하지 않는다.
     */
    object Tags {
        const val CHANNEL = "channel"
        const val STATUS = "status"
        const val CLASSIFICATION = "classification"
        const val FAILURE_CATEGORY = "failure_category"
        const val RESULT = "result"
    }

    /** adapter가 상태나 분류를 알 수 없는 실패에서도 동일한 tag schema를 유지한다. */
    object TagValues {
        const val UNKNOWN = "UNKNOWN"
        const val NONE = "NONE"
    }

    /** 각 처리 단계가 노출할 수 있는 안정적인 result tag 값이다. */
    object Results {
        object Dispatch {
            const val SENT = "sent"
            const val RETRY_WAIT = "retry_wait"
            const val DEAD = "dead"
            const val DUPLICATED = "duplicated"
            const val UNEXPECTED_STATUS = "unexpected_status"
            const val INVALID_PAYLOAD = "invalid_payload"
            const val NOT_READY = "not_ready"
            const val FAILED = "failed"
        }

        object Sender {
            const val SUCCESS = "success"
            const val RATE_LIMITED = "rate_limited"
            const val TRANSIENT_FAILURE = "transient_failure"
            const val PERMANENT_FAILURE = "permanent_failure"
            const val UNEXPECTED = "unexpected"
        }

        object Dlt {
            const val PERSISTED = "persisted"
            const val PERSIST_FAILED = "persist_failed"
        }

        object ProcessingRecoveryTick {
            const val COMPLETED = "completed"
            const val FAILED = "failed"
        }

        /** 한 번의 recovery tick에서 실제로 처리한 notification 단건 수량을 분류한다. */
        object ProcessingRecovery {
            const val RETRY_WAIT = "retry_wait"
            const val DEAD = "dead"
            const val SKIPPED = "skipped"
        }
    }
}
