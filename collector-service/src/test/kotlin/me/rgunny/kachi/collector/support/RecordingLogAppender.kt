package me.rgunny.kachi.collector.support

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * 테스트 동안만 대상 로거에 appender를 붙여, 그 사이에 찍힌 로그 메시지를 모아 두는 테스트 도구.
 *
 * scheduler tick처럼 반환값도 상태 변화도 없이 로그만 남기는 코드는 로그가 유일한 검증 대상이다.
 * appender를 떼지 않으면 다음 테스트의 로그까지 섞이므로 [use] 블록 안에서만 쓴다.
 */
class RecordingLogAppender private constructor(
    private val logger: Logger,
    private val appender: ListAppender<ILoggingEvent>
) : AutoCloseable {

    fun messagesAt(level: Level): List<String> {
        return appender.list.filter { it.level == level }.map { it.formattedMessage }
    }

    override fun close() {
        logger.detachAppender(appender)
    }

    companion object {

        fun attachTo(type: KClass<*>): RecordingLogAppender {
            val logger = LoggerFactory.getLogger(type.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)

            return RecordingLogAppender(logger, appender)
        }
    }
}
