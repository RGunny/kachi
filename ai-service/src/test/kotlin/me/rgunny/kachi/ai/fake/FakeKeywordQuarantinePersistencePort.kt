package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.quarantine.KeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.run.AiRunTargetType

/**
 * in-memory 키워드 격리 기록 저장소.
 *
 * targetType + keyword unique index를 흉내 내어 save가 같은 키의 기존 기록을 대체한다.
 * 실패 없이 끝난 실행이 기록을 남기지 않는지 확인할 수 있도록 saveCount를 센다.
 */
class FakeKeywordQuarantinePersistencePort : KeywordQuarantinePersistencePort {
    val quarantines: MutableList<KeywordQuarantine> = mutableListOf()
    val savedOutboxes: MutableList<AiOutbox> = mutableListOf()
    var saveCount: Int = 0
    var saveQuarantinedCount: Int = 0

    override suspend fun findAllBy(targetType: AiRunTargetType): List<KeywordQuarantine> {
        return quarantines.filter { it.targetType == targetType }
    }

    override suspend fun findBy(targetType: AiRunTargetType, keyword: AiKeyword): KeywordQuarantine? {
        return quarantines.firstOrNull { it.targetType == targetType && it.keyword == keyword }
    }

    override suspend fun save(quarantine: KeywordQuarantine): KeywordQuarantine {
        saveCount += 1
        quarantines.removeAll { it.targetType == quarantine.targetType && it.keyword == quarantine.keyword }
        quarantines.add(quarantine)

        return quarantine
    }

    override suspend fun saveQuarantined(
        quarantine: KeywordQuarantine,
        outbox: AiOutbox
    ): KeywordQuarantine {
        saveQuarantinedCount += 1
        savedOutboxes.add(outbox)

        return save(quarantine)
    }

    fun findByKeyword(keyword: AiKeyword): KeywordQuarantine? {
        return quarantines.firstOrNull { it.keyword == keyword }
    }
}
