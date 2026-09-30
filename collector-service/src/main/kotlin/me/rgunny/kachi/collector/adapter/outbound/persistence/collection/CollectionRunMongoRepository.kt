package me.rgunny.kachi.collector.adapter.outbound.persistence.collection

import java.util.UUID
import org.springframework.data.mongodb.repository.ReactiveMongoRepository

interface CollectionRunMongoRepository : ReactiveMongoRepository<CollectionRunMongoDocument, UUID>
