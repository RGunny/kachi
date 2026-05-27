package me.rgunny.kachi.collector.adapter.out.client.google

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.collector.application.port.out.CollectedArticle
import me.rgunny.kachi.collector.application.port.out.NewsProviderPort
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

class GoogleNewsRssProvider(
    private val webClient: WebClient,
    private val properties: GoogleNewsProperties
) : NewsProviderPort {

    override val source: NewsSource = NewsSource.GOOGLE

    override suspend fun collect(keyword: CollectedKeyword): List<CollectedArticle> {
        // 1. Google News RSS 요청 URI를 만든다.
        val rssUrl = UriComponentsBuilder.fromUriString(properties.baseUrl)
            .queryParam("q", keyword.value)
            .queryParam("hl", properties.languageCode)
            .queryParam("gl", properties.countryCode)
            .queryParam("ceid", "${properties.countryCode}:${properties.languageCode}")
            .build()
            .toUri()

        // 2. WebClient로 Google News RSS를 호출하고, 응답 body를 String XML로 받는다.
        val xml = webClient.get()
            .uri(rssUrl)
            .retrieve()
            .bodyToMono(String::class.java)
            .timeout(properties.timeout)
            .awaitSingle()

        // 3. parseRss(xml)로 RSS item 목록을 만든다.
        val rssItems = parseRss(xml)

        // 4. 제목이나 URL이 비어 있는 item은 adapter 안에서 제외한다.
        // TODO: 상세 실패분리는 추후 고도화
        return rssItems
            .filter { it.title.isNotBlank() && it.link.isNotBlank() }
            .map { toCollectedArticle(it) }
    }

    private fun parseRss(xml: String): List<GoogleRssItem> {
        // 1. JDK DocumentBuilderFactory를 만든다.
        // 2. XXE 방지를 위해 외부 entity, DTD 로딩을 비활성화한다.
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false

            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

        // 3. XML 문자열을 Document로 파싱한다.
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        // 4. <item> 노드를 순회하며 title/link/pubDate/source 값을 읽는다.
        val itemNodes = document.getElementsByTagName("item")
        val items = mutableListOf<GoogleRssItem>()

        for (index in 0 until itemNodes.length) {
            val item = itemNodes.item(index) as? Element ?: continue
            val title = item.textContentOf("title") ?: continue
            val link = item.textContentOf("link") ?: continue

            // 5. 필수값 title/link가 없으면 해당 item은 제외한다.
            if (title.isBlank() || link.isBlank()) continue

            items.add(
                GoogleRssItem(
                    title = title,
                    link = link,
                    pubDate = item.textContentOf("pubDate"),
                    sourceName = item.textContentOf("source")
                )
            )
        }

        return items
    }

    /**
     * Google RSS item을 application 계층이 사용하는 수집 기사 DTO로 변환한다.
     */
    private fun toCollectedArticle(item: GoogleRssItem): CollectedArticle {
        return CollectedArticle(
            source = NewsSource.GOOGLE,
            title = item.title.trim(),
            url = item.link,
            publishedAt = parsePublishedAt(item.pubDate)
        )
    }

    /**
     * Google RSS pubDate를 Instant로 변환한다. 파싱할 수 없으면 해당 기사 시각만 null로 둔다.
     */
    private fun parsePublishedAt(pubDate: String?): Instant? {
        if (pubDate.isNullOrBlank()) return null

        return runCatching {
            ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull()
    }

    /**
     * XML element에서 첫 번째 tagName 하위 노드의 textContent를 앞뒤 공백 제거 후 반환한다.
     */
    private fun Element.textContentOf(tagName: String): String? {
        val nodes = getElementsByTagName(tagName)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent?.trim()
    }

}
