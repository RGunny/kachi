package me.rgunny.kachi.ai.application.port.inbound.quarantine

import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineCommand
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineResult

/**
 * 운영자가 원인을 확인한 격리를 해제해 다시 요약 대상으로 되돌린다.
 */
interface ReleaseKeywordQuarantineUseCase {

    suspend fun release(command: ReleaseKeywordQuarantineCommand): ReleaseKeywordQuarantineResult
}
