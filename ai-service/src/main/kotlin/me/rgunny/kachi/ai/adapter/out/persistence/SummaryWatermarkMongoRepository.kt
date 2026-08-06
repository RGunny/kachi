package me.rgunny.kachi.ai.adapter.out.persistence

import org.springframework.data.mongodb.repository.ReactiveMongoRepository

/**
 * `ai_summary_watermarks` 접근 repository.
 *
 * 대상 종류별로 문서가 한 건뿐이라 조회 조건이 필요 없고, id 타입은 `_id`로 쓰는 targetType 문자열이다.
 */
interface SummaryWatermarkMongoRepository : ReactiveMongoRepository<SummaryWatermarkMongoDocument, String>
