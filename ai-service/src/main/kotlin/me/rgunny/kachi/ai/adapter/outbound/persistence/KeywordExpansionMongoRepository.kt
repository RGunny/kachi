package me.rgunny.kachi.ai.adapter.outbound.persistence

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import java.util.UUID

interface KeywordExpansionMongoRepository : ReactiveMongoRepository<KeywordExpansionMongoDocument, UUID>
