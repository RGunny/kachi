package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.binding.ListChannelBindingsUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.ChannelBindingResult
import me.rgunny.kachi.user.application.port.inbound.binding.model.ListChannelBindingsQuery
import me.rgunny.kachi.user.application.port.outbound.binding.ChannelBindingPersistencePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자의 바인딩 목록. 채널 순으로 돌려주고 주소는 마스킹한다.
 */
@Service
@Transactional(readOnly = true)
class ChannelBindingQueryService(
    private val channelBindingPersistencePort: ChannelBindingPersistencePort
) : ListChannelBindingsUseCase {

    override fun list(query: ListChannelBindingsQuery): List<ChannelBindingResult> {
        return channelBindingPersistencePort.findAllByUserId(query.userId)
            .sortedBy { it.channel }
            .map(ChannelBindingResult::of)
    }
}
