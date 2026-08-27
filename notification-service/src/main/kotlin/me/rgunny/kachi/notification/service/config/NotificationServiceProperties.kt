package me.rgunny.kachi.notification.service.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.notification")
data class NotificationServiceProperties(
    val request: Request,
    val dispatch: Dispatch,
    val outbox: Outbox,
){

    data class Request(
        val topic: String,
        val groupId: String,
        val autoOffsetReset: String,
        val dedupeTtl: Duration,
        val dlt: Dlt,
        val retry: Retry,
    ) {

        data class Dlt(
            val topic: String,
        )

        data class Retry(
            val maxAttempts: Long,
            val backoff: Duration,
        )
    }

    data class Dispatch(
        val topic: String,
    )

    data class Outbox(
        val publisherId: String,
        val batchSize: Int,
        val pollInterval: Duration,
        val publishingVisibilityTimeout: Duration,
        val retry: Retry,
        val scheduler: Scheduler,
    ) {

        data class Retry(
            val maxAttempts: Int,
            val baseDelay: Duration,
            val maxDelay: Duration,
        )

        data class Scheduler(
            val enabled: Boolean,
        )

    }

}
