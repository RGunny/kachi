package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.exception.KeywordAccessDeniedException
import me.rgunny.kachi.user.application.exception.KeywordNotFoundException
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordResult
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordUseCase
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordResult
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordUseCase
import me.rgunny.kachi.user.application.port.out.KeywordPersistencePort
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class KeywordCommandService(
    private val keywordPersistencePort: KeywordPersistencePort,
    private val activeUserValidator: ActiveUserValidator,
    private val clock: Clock
) : RegisterKeywordUseCase, UpdateKeywordUseCase {

    override fun register(command: RegisterKeywordCommand): RegisterKeywordResult {
        activeUserValidator.get(command.userId)

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

    override fun update(command: UpdateKeywordCommand): UpdateKeywordResult {
        val keyword = keywordPersistencePort.findById(command.keywordId)
            ?: throw KeywordNotFoundException(command.keywordId)

        if (keyword.userId != command.userId) {
            throw KeywordAccessDeniedException(command.keywordId, command.userId)
        }

        activeUserValidator.get(keyword.userId)

        var updatedKeyword = keyword

        if (command.name != null) {
            val name = KeywordName.of(command.name)

            if (keyword.name != name && keywordPersistencePort.existsByUserIdAndName(keyword.userId, name)) {
                throw DuplicateKeywordException(keyword.userId, name)
            }

            updatedKeyword = updatedKeyword.rename(name)
        }

        if (command.enabled != null) {
            updatedKeyword = if (command.enabled) {
                updatedKeyword.enable()
            } else {
                updatedKeyword.disable(Instant.now(clock))
            }
        }

        return UpdateKeywordResult.from(keywordPersistencePort.save(updatedKeyword))
    }
}
