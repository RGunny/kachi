package me.rgunny.kachi.story.fake

/**
 * [FakeKafkaTemplate]이 기록하는 발행 레코드.
 */
data class SentKafkaRecord(
    val topic: String,
    val key: String,
    val value: String
)
