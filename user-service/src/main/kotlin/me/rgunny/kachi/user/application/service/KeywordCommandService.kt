package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordResult
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordUseCase
import me.rgunny.kachi.user.application.port.out.KeywordPersistencePort
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class KeywordCommandService(
    private val keywordPersistencePort: KeywordPersistencePort,
    private val clock: Clock
) : RegisterKeywordUseCase {

    override fun register(command: RegisterKeywordCommand): RegisterKeywordResult {
        val name = KeywordName.of(command.name)

        if (keywordPersistencePort.existsByUserIdAndName(command.userId, name)) {
            throw DuplicateKeywordException(command.userId, name)
        }

        val keyword = Keyword.create(
            userId = command.userId,
            name = name,
            registeredAt = Instant.now(clock)
        )

        return RegisterKeywordResult.from(keywordPersistencePort.save(keyword))
    }
}
