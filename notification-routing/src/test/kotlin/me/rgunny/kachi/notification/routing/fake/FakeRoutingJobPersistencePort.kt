package me.rgunny.kachi.notification.routing.fake

import me.rgunny.kachi.notification.routing.application.port.outbound.persistence.RoutingJobPersistencePort
import me.rgunny.kachi.notification.routing.domain.RoutingJob
import me.rgunny.kachi.notification.routing.exception.routing.RoutingJobConflictException

/**
 * eventKey unique를 흉내 내는 in-memory routing job 저장소.
 *
 * conflictOnce가 켜져 있으면 다음 insert 한 번을 충돌로 처리하면서 conflictJob을 몰래 넣어
 * "다른 인스턴스가 먼저 만든" 상황을 만든다.
 */
class FakeRoutingJobPersistencePort : RoutingJobPersistencePort {
    val jobsByEventKey = mutableMapOf<String, RoutingJob>()
    val inserted = mutableListOf<RoutingJob>()
    val saved = mutableListOf<RoutingJob>()
    var conflictOnce: RoutingJob? = null

    fun put(job: RoutingJob) {
        jobsByEventKey[job.eventKey] = job
    }

    override suspend fun insert(job: RoutingJob): RoutingJob {
        conflictOnce?.let { existing ->
            conflictOnce = null
            put(existing)
            throw RoutingJobConflictException(job.eventKey)
        }
        if (jobsByEventKey.containsKey(job.eventKey)) {
            throw RoutingJobConflictException(job.eventKey)
        }
        inserted += job
        put(job)
        return job
    }

    override suspend fun findByEventKey(eventKey: String): RoutingJob? {
        return jobsByEventKey[eventKey]
    }

    override suspend fun save(job: RoutingJob): RoutingJob {
        saved += job
        put(job)
        return job
    }
}
