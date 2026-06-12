package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.fake.FakeSender
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@DisplayName("NotificationSenderRouter")
class NotificationSenderRouterTest {

    @Test
    @DisplayName("채널을 지원하는 sender가 하나면 해당 sender를 반환한다")
    fun route() {
        val slackSender = FakeSender(NotificationChannel.SLACK)
        val emailSender = FakeSender(NotificationChannel.EMAIL)
        val router = NotificationSenderRouter(listOf(slackSender, emailSender))

        assertSame(slackSender, router.route(NotificationChannel.SLACK))
    }

    @Test
    @DisplayName("채널을 지원하는 sender가 없으면 예외를 던진다")
    fun rejectMissingSender() {
        val router = NotificationSenderRouter(listOf(FakeSender(NotificationChannel.EMAIL)))

        assertFailsWith<IllegalArgumentException> {
            router.route(NotificationChannel.SLACK)
        }
    }

    @Test
    @DisplayName("채널을 지원하는 sender가 둘 이상이면 예외를 던진다")
    fun rejectMultipleSenders() {
        val router = NotificationSenderRouter(
            listOf(FakeSender(NotificationChannel.SLACK), FakeSender(NotificationChannel.SLACK))
        )

        assertFailsWith<IllegalStateException> {
            router.route(NotificationChannel.SLACK)
        }
    }
}
