package me.rgunny.kachi.collector.adapter.out.persistence

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import java.util.UUID

interface CollectionRunMongoRepository : ReactiveMongoRepository<CollectionRunMongoDocument, UUID>
