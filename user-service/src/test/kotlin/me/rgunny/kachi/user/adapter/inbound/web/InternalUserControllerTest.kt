package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeFindUsersByRoleUseCase
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

@DisplayName("InternalUserController")
class InternalUserControllerTest {
    private val findUsersByRoleUseCase = FakeFindUsersByRoleUseCase()
    private val controller = InternalUserController(findUsersByRoleUseCase)

    @Test
    @DisplayName("역할을 질의로 옮기고 사용자 id 문자열과 채널 목록을 정렬해 돌려준다")
    fun mapUsersByRole() {
        val response = controller.findUsersByRole(UserRole.ADMIN)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(UserRole.ADMIN, findUsersByRoleUseCase.query.role)
        val body = response.body?.data?.single()
        assertEquals(36, body?.userId?.length)
        assertEquals(listOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM), body?.channels)
    }
}
