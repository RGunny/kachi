package me.rgunny.kachi.ai.application.port.inbound.quarantine

import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseStoryQuarantineCommand
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseStoryQuarantineResult

/**
 * 운영자가 원인 해소를 확인한 뒤 story 격리를 해제하는 유스케이스.
 */
interface ReleaseStoryQuarantineUseCase {

    suspend fun release(command: ReleaseStoryQuarantineCommand): ReleaseStoryQuarantineResult
}
