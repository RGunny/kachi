package me.rgunny.kachi.collector.application.service

import me.rgunny.kachi.collector.application.port.`in`.ListNewsQuery
import me.rgunny.kachi.collector.application.port.`in`.ListNewsResult
import me.rgunny.kachi.collector.application.port.`in`.ListNewsUseCase
import me.rgunny.kachi.collector.application.port.out.NewsPersistencePort
import org.springframework.stereotype.Service

@Service
class NewsQueryService(
    private val newsPersistencePort: NewsPersistencePort
) : ListNewsUseCase {

    override suspend fun listNews(query: ListNewsQuery): List<ListNewsResult> {
        return newsPersistencePort.findByKeyword(
            keyword = query.keyword,
            from = query.from,
            to = query.to,
            limit = query.limit
        ).map(ListNewsResult::from)
    }
}
