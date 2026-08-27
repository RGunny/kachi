package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.ChannelBindingRefNotFoundException
import me.rgunny.kachi.user.application.port.inbound.internal.ResolveChannelBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolveChannelBindingQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolvedChannelBindingResult
import me.rgunny.kachi.user.application.port.outbound.binding.ChannelBindingPersistencePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 수신처 참조를 주소로 푼다.
 * 없는 참조는 예외이고, 있는데 ACTIVE가 아니면 주소 없이 상태만 돌려준다.
 */
@Service
@Transactional(readOnly = true)
class ChannelBindingResolveService(
    private val channelBindingPersistencePort: ChannelBindingPersistencePort
) : ResolveChannelBindingUseCase {

    override fun resolve(query: ResolveChannelBindingQuery): ResolvedChannelBindingResult {
        val binding = channelBindingPersistencePort.findById(query.ref)
            ?: throw ChannelBindingRefNotFoundException(query.ref)

        return ResolvedChannelBindingResult.of(binding)
    }
}
