package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.application.port.inbound.internal.FindSubscribersUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.FindSubscribersQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.SubscriberResult
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

@DisplayName("InternalSubscriptionController")
class InternalSubscriptionControllerTest {

    @Test
    @DisplayName("키워드를 질의로 옮기고 수신자를 id 문자열로 돌려준다")
    fun mapSubscribers() {
        val userId = UserId.newId()
        var received: FindSubscribersQuery? = null
        val controller = InternalSubscriptionController(
            object : FindSubscribersUseCase {
                override fun findSubscribers(query: FindSubscribersQuery): List<SubscriberResult> {
                    received = query
                    return listOf(SubscriberResult(userId, SubscriptionChannel.TELEGRAM))
                }
            }
        )

        val response = controller.findSubscribers("tesla")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("tesla", received?.keyword)
        val body = response.body?.data?.single()
        assertEquals(userId.value.toString(), body?.userId)
        assertEquals(SubscriptionChannel.TELEGRAM, body?.channel)
    }
}
