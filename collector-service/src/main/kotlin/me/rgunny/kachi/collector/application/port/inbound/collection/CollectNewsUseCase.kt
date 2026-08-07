package me.rgunny.kachi.collector.application.port.inbound.collection

import me.rgunny.kachi.collector.application.port.inbound.collection.model.CollectNewsCommand
import me.rgunny.kachi.collector.application.port.inbound.collection.model.CollectionRunResult

/** 뉴스 수집 실행 유스케이스를 입력 어댑터에 제공하는 포트 */
interface CollectNewsUseCase {

    suspend fun collect(command: CollectNewsCommand): CollectionRunResult
}
