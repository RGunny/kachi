package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.internal.FindUsersByRoleUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.FindUsersByRoleQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.UserChannelsResult
import me.rgunny.kachi.user.application.port.outbound.binding.ChannelBindingPersistencePort
import me.rgunny.kachi.user.application.port.outbound.user.UserPersistencePort
import me.rgunny.kachi.user.domain.UserStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 역할의 수신자 조회.
 *
 * ACTIVE 사용자마다 ACTIVE 바인딩 채널을 모으고, 채널이 하나도 없는 사용자는 뺀다.
 * 구독 조회와 달리 키워드를 거치지 않는다.
 */
@Service
@Transactional(readOnly = true)
class UserChannelsQueryService(
    private val userPersistencePort: UserPersistencePort,
    private val channelBindingPersistencePort: ChannelBindingPersistencePort
) : FindUsersByRoleUseCase {

    override fun findUsersByRole(query: FindUsersByRoleQuery): List<UserChannelsResult> {
        // 1. 역할의 ACTIVE 사용자.
        val users = userPersistencePort.findAllByRole(query.role)
            .filter { it.status == UserStatus.ACTIVE }
        if (users.isEmpty()) {
            return emptyList()
        }

        // 2. 그 사용자들의 ACTIVE 바인딩을 한 번에 읽어 사용자별 채널로 묶는다.
        val activeChannels = channelBindingPersistencePort
            .findAllByUserIds(users.map { it.id }.toSet())
            .filter { it.isActive }
            .groupBy({ it.userId }, { it.channel })

        // 3. 채널이 있는 사용자만 사용자 순으로.
        return users
            .mapNotNull { user ->
                activeChannels[user.id]?.let { channels -> UserChannelsResult(userId = user.id, channels = channels.toSet()) }
            }
            .sortedBy { it.userId.value }
    }
}
