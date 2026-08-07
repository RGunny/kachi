package me.rgunny.kachi.user.adapter.outbound.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    UserPersistenceAdapter::class,
    KeywordPersistenceAdapter::class,
    PersistenceAdapterTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
