package me.rgunny.kachi.ai.adapter.out.persistence

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import java.util.UUID

interface NewsSummaryMongoRepository : ReactiveMongoRepository<NewsSummaryMongoDocument, UUID>
