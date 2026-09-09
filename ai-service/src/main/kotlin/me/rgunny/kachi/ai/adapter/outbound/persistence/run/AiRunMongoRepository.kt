package me.rgunny.kachi.ai.adapter.outbound.persistence.run

import java.util.UUID
import org.springframework.data.mongodb.repository.ReactiveMongoRepository

interface AiRunMongoRepository : ReactiveMongoRepository<AiRunMongoDocument, UUID>
