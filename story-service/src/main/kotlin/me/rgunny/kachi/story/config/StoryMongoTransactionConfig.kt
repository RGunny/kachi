package me.rgunny.kachi.story.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * story-service MongoDB transaction 설정.
 */
@Configuration
class StoryMongoTransactionConfig {

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
