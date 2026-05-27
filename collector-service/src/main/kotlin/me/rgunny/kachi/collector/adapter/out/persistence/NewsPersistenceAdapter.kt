package me.rgunny.kachi.collector.adapter.out.persistence

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.collector.application.port.out.NewsPersistencePort
import me.rgunny.kachi.collector.application.port.out.SaveNewsResult
import me.rgunny.kachi.collector.domain.News
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component

@Component
class NewsPersistenceAdapter(
    private val repository: NewsMongoRepository
) : NewsPersistencePort {

    override suspend fun findExistingUrlHashes(urlHashes: Set<String>): Set<String> {
        if (urlHashes.isEmpty()) return emptySet()

        return repository.findByUrlHashIn(urlHashes)
            .map { it.urlHash }
            .collectList()
            .awaitSingle()
            .toSet()
    }

    override suspend fun save(news: News): SaveNewsResult {
        return try {
            repository.save(NewsMongoDocument.fromDomain(news))
                .awaitSingle()
            SaveNewsResult.SAVED
        } catch (e: DuplicateKeyException) {
            SaveNewsResult.DUPLICATED
        }
    }
}
