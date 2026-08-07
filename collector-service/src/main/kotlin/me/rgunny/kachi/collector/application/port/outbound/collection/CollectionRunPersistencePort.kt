package me.rgunny.kachi.collector.application.port.outbound.collection

import me.rgunny.kachi.collector.domain.CollectionRun
import me.rgunny.kachi.collector.domain.CollectionRunId

/** 수집 실행 기록 저장소 접근을 application 계층에 제공하는 출력 포트 */
interface CollectionRunPersistencePort {

    suspend fun findById(id: CollectionRunId): CollectionRun?

    suspend fun save(collectionRun: CollectionRun): CollectionRun
}
