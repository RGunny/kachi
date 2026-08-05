package me.rgunny.kachi.notification.worker.fake

import org.springframework.kafka.support.Acknowledgment

/**
 * Kafka listener가 ack 여부를 결정했는지만 확인하기 위한 Acknowledgment fake.
 */
class FakeAcknowledgment : Acknowledgment {
    var acked: Boolean = false
        private set

    override fun acknowledge() {
        acked = true
    }
}
