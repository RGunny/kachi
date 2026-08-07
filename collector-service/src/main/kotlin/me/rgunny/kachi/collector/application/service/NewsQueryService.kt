package me.rgunny.kachi.collector.application.service

import me.rgunny.kachi.collector.application.port.inbound.news.model.ListNewsQuery
import me.rgunny.kachi.collector.application.port.inbound.news.model.ListNewsResult
import me.rgunny.kachi.collector.application.port.inbound.news.ListNewsUseCase
import me.rgunny.kachi.collector.application.port.outbound.news.NewsPersistencePort
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
