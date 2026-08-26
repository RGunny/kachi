package me.rgunny.kachi.user.adapter.outbound.persistence

import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Testcontainers MySQL 위에서 persistence adapter를 검증하는 공통 베이스.
 *
 * 실제 스키마(Flyway)와 제약을 그대로 쓰므로 unique·FK·collation 동작을 여기서 본다.
 */
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    UserPersistenceAdapter::class,
    KeywordPersistenceAdapter::class,
    SubscriptionPersistenceAdapter::class,
    PersistenceAdapterTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest {

    @Autowired
    protected lateinit var entityManager: EntityManager

    /** 영속성 컨텍스트를 비워 다음 조회가 1차 캐시가 아니라 DB를 읽게 한다. unique 위반은 flush 시점에 드러난다. */
    protected fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
