package me.rgunny.kachi.collector.fake

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaProducerException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

/**
 * 보낸 레코드를 기록하고 레코드별로 지정한 실패를 돌려주는 KafkaTemplate.
 *
 * 발행 어댑터 단위 테스트용이다. broker에 붙지 않으며 컨텍스트도 필요 없다.
 * 실패는 실제 template이 그러듯 [KafkaProducerException]으로 감싸 future에 싣는다.
 */
class FakeKafkaTemplate : KafkaTemplate<String, String>(DefaultKafkaProducerFactory<String, String>(emptyMap())) {
    val sent: MutableList<SentRecord> = mutableListOf()
    var failureFor: (SentRecord) -> Throwable? = { null }

    override fun send(topic: String, key: String, data: String?): CompletableFuture<SendResult<String, String>> {
        val record = SentRecord(topic = topic, key = key, value = requireNotNull(data))
        sent.add(record)

        val producerRecord = ProducerRecord(topic, key, data)
        val failure = failureFor(record)
            ?: return CompletableFuture.completedFuture(
                SendResult(producerRecord, RecordMetadata(TopicPartition(topic, 0), 0, 0, 0, 0, 0))
            )

        return CompletableFuture.failedFuture(KafkaProducerException(producerRecord, "Failed to send", failure))
    }

    data class SentRecord(
        val topic: String,
        val key: String,
        val value: String
    )
}
