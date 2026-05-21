package me.rgunny.kachi.user.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    UserPersistenceAdapter::class,
    KeywordPersistenceAdapter::class,
    PersistenceAdapterTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
