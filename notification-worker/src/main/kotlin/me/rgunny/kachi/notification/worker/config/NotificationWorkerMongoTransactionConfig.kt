package me.rgunny.kachi.notification.worker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * notification-worker MongoDB transaction 설정.
 *
 * dispatch claim/finalize는 notifications 상태 변경과 notification_histories append를
 * 같은 MongoDB transaction 안에서 확정한다.
 */
@Configuration
class NotificationWorkerMongoTransactionConfig {

    @Bean
    fun reactiveMongoTransactionManager(
        databaseFactory: ReactiveMongoDatabaseFactory,
    ): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(databaseFactory)
    }

    @Bean
    fun transactionalOperator(
        transactionManager: ReactiveTransactionManager,
    ): TransactionalOperator {
        return TransactionalOperator.create(transactionManager)
    }
}
