package me.rgunny.kachi.notification.routing.application.port.outbound.persistence

import me.rgunny.kachi.notification.routing.domain.RoutingJob

/**
 * routing job 영속화 port.
 *
 * eventKey는 저장소 unique 제약으로 보호해야 한다. insert가 그 제약에 걸리면
 * RoutingJobConflictException을 던지고, 호출자는 다시 조회해 기존 job으로 진행한다.
 */
interface RoutingJobPersistencePort {

    suspend fun insert(job: RoutingJob): RoutingJob

    suspend fun findByEventKey(eventKey: String): RoutingJob?

    suspend fun save(job: RoutingJob): RoutingJob
}
