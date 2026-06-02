package me.rgunny.kachi.collector.application.port.out

import me.rgunny.kachi.collector.domain.CollectedKeyword

/** 수집 대상 키워드 공급을 application 계층에 제공하는 출력 포트 */
interface KeywordReaderPort {

    suspend fun findActiveKeywords(): List<CollectedKeyword>
}
