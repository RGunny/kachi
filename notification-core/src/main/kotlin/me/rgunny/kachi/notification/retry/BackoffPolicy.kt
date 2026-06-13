package me.rgunny.kachi.notification.retry

import java.time.Duration

interface BackoffPolicy {
    fun delay(attempts: Int): Duration
}
