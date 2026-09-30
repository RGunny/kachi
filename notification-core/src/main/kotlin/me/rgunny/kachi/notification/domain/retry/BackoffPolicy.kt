package me.rgunny.kachi.notification.domain.retry

import java.time.Duration

interface BackoffPolicy {
    fun delay(attempts: Int): Duration
}
