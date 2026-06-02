package me.rgunny.kachi.collector.application.port.`in`

/** 뉴스 수집 실행 유스케이스를 입력 어댑터에 제공하는 포트 */
interface CollectNewsUseCase {

    suspend fun collect(command: CollectNewsCommand): CollectionRunResult
}
