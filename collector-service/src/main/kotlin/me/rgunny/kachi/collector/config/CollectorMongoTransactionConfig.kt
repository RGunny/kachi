package me.rgunny.kachi.collector.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * collector-service MongoDB transaction 설정.
 *
 * MongoDB transaction은 replica set 또는 sharded cluster에서만 동작한다.
 * 로컬 profile은 단일 노드 replica set으로 실행하고, 운영은 다중 노드 replica set 또는 sharded cluster를 전제로 한다.
 */
@Configuration
class CollectorMongoTransactionConfig {

    @Bean
    fun reactiveMongoTransactionManager(
        databaseFactory: ReactiveMongoDatabaseFactory
    ): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(databaseFactory)
    }

    @Bean
    fun transactionalOperator(
        transactionManager: ReactiveTransactionManager
    ): TransactionalOperator {
        return TransactionalOperator.create(transactionManager)
    }
}
