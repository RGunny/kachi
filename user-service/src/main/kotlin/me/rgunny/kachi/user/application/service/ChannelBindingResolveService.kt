package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.ChannelBindingNotFoundException
import me.rgunny.kachi.user.application.port.inbound.internal.ResolveChannelBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolveChannelBindingQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolvedChannelBindingResult
import me.rgunny.kachi.user.application.port.outbound.binding.ChannelBindingPersistencePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 수신자와 채널로 수신 주소를 푼다.
 * 바인딩이 없으면 예외이고, 있는데 ACTIVE가 아니면 주소 없이 상태만 돌려준다.
 */
@Service
@Transactional(readOnly = true)
class ChannelBindingResolveService(
    private val channelBindingPersistencePort: ChannelBindingPersistencePort
) : ResolveChannelBindingUseCase {

    override fun resolve(query: ResolveChannelBindingQuery): ResolvedChannelBindingResult {
        val binding = channelBindingPersistencePort.findByUserIdAndChannel(query.userId, query.channel)
            ?: throw ChannelBindingNotFoundException(query.userId, query.channel)

        return ResolvedChannelBindingResult.of(binding)
    }
}
