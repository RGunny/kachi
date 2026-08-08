package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.outbound.persistence.KeywordJpaEntity
import me.rgunny.kachi.user.adapter.outbound.persistence.KeywordJpaRepository
import me.rgunny.kachi.user.adapter.outbound.persistence.UserJpaEntity
import me.rgunny.kachi.user.adapter.outbound.persistence.UserJpaRepository
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.User
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock
import java.time.Instant

@Configuration
@Profile("local")
class LocalSeedDataConfig {

    @Bean
    fun localSeedDataInitializer(
        userJpaRepository: UserJpaRepository,
        keywordJpaRepository: KeywordJpaRepository,
        clock: Clock
    ): ApplicationRunner {
        return ApplicationRunner {
            val now = Instant.now(clock)
            val seedUser = findOrCreateSeedUser(userJpaRepository, now)
            val savedKeywords = seedKeywords(keywordJpaRepository, seedUser, now)

            log.info(
                "Local seed data initialized userId={} keywordCount={} keywords={}",
                seedUser.id,
                savedKeywords.size,
                savedKeywords.map { it.name.value }
            )
        }
    }

    private fun findOrCreateSeedUser(userJpaRepository: UserJpaRepository, registeredAt: Instant): User {
        val existingUser = userJpaRepository.findByEmail(SEED_USER_EMAIL)
        if (existingUser != null) {
            return existingUser.toDomain()
        }

        val seedUser = User.register(
            email = Email.of(SEED_USER_EMAIL),
            nickname = Nickname.of(SEED_USER_NICKNAME),
            authProvider = AuthProvider.LOCAL,
            providerUserId = null,
            registeredAt = registeredAt
        )

        return userJpaRepository.save(UserJpaEntity.from(seedUser)).toDomain()
    }

    private fun seedKeywords(
        keywordJpaRepository: KeywordJpaRepository,
        seedUser: User,
        registeredAt: Instant
    ): List<Keyword> {
        return SEED_KEYWORD_NAMES.map { keywordName ->
            val name = KeywordName.of(keywordName)
            val existingKeyword = keywordJpaRepository.findByUserIdAndName(seedUser.id.value, name.value)

            if (existingKeyword == null) {
                val keyword = Keyword.create(
                    userId = seedUser.id,
                    name = name,
                    registeredAt = registeredAt
                )
                keywordJpaRepository.save(KeywordJpaEntity.from(keyword)).toDomain()
            } else {
                val keyword = existingKeyword.toDomain()
                if (keyword.enabled) {
                    keyword
                } else {
                    keywordJpaRepository.save(KeywordJpaEntity.from(keyword.enable())).toDomain()
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalSeedDataConfig::class.java)

        private const val SEED_USER_EMAIL = "collector-admin@kachi.local"
        private const val SEED_USER_NICKNAME = "collector-admin"
        private val SEED_KEYWORD_NAMES = listOf("TRUMP", "NVIDIA", "SPACE-X", "TESLA", "이란")
    }
}
