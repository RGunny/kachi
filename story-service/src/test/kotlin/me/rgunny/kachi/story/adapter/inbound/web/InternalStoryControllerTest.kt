package me.rgunny.kachi.story.adapter.inbound.web

import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.rgunny.kachi.story.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.story.application.exception.StoryOperationErrorCode
import me.rgunny.kachi.story.application.exception.StoryOperationException
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeStoryPairResult
import me.rgunny.kachi.story.application.port.inbound.split.model.SplitStoryResult
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesQuery
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesResult
import me.rgunny.kachi.story.application.port.inbound.story.model.StoryDetail
import me.rgunny.kachi.story.config.ApiVersionConfig
import me.rgunny.kachi.story.domain.AutoMergedLinkDecision
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryStatus
import me.rgunny.kachi.story.fake.RecordingFindStoriesUseCase
import me.rgunny.kachi.story.fake.RecordingGetStoryUseCase
import me.rgunny.kachi.story.fake.RecordingMergeStoryPairUseCase
import me.rgunny.kachi.story.fake.RecordingSplitStoryUseCase
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import me.rgunny.kachi.story.fixture.StoryTestFixture.article
import me.rgunny.kachi.story.fixture.StoryTestFixture.story
import me.rgunny.kachi.story.support.JsonBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(controllers = [InternalStoryController::class])
@Import(ApiVersionConfig::class, InternalStoryControllerTest.TestBeans::class)
@DisplayName("InternalStoryController")
class InternalStoryControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var findUseCase: RecordingFindStoriesUseCase

    @Autowired
    private lateinit var getUseCase: RecordingGetStoryUseCase

    @Autowired
    private lateinit var mergeUseCase: RecordingMergeStoryPairUseCase

    @Autowired
    private lateinit var splitUseCase: RecordingSplitStoryUseCase

    @BeforeEach
    fun resetUseCases() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        findUseCase.result = FindStoriesResult(emptyList())
        findUseCase.lastQuery = null
        findUseCase.invokeCount = 0
        getUseCase.detail = null
        getUseCase.failure = null
        getUseCase.lastStoryId = null
        mergeUseCase.result = null
        mergeUseCase.failure = null
        mergeUseCase.lastCommand = null
        splitUseCase.result = null
        splitUseCase.failure = null
        splitUseCase.lastCommand = null
    }

    @Test
    @DisplayName("목록 조회는 파라미터를 조건으로 옮기고 centroid 없는 story를 응답한다")
    fun findStories() {
        val story = story()
        findUseCase.result = FindStoriesResult(listOf(story))
        val from = NOW.minus(Duration.ofHours(24))

        val body = webTestClient.get()
            .uri { builder ->
                builder.path(ApiPaths.V1_INTERNAL_STORIES)
                    .queryParam("status", StoryStatus.OPEN.name)
                    .queryParam("from", from.toString())
                    .queryParam("limit", 10)
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        val row = json.get("data").get(0)
        assertEquals(story.id.value.toString(), row.get("id").asString())
        assertEquals(StoryStatus.OPEN.name, row.get("status").asString())
        assertEquals(1, row.get("articleCount").asInt())
        assertTrue(!row.has("centroid"), "목록 응답에 centroid가 없어야 합니다")

        val query = requireNotNull(findUseCase.lastQuery)
        assertEquals(StoryStatus.OPEN, query.status)
        assertEquals(from, query.openedAfter)
        assertEquals(10, query.limit)
    }

    @Test
    @DisplayName("조회 조건을 생략하면 전체 상태를 기본 개수만큼 조회한다")
    fun findStoriesWithDefaults() {
        webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_STORIES)
            .exchange()
            .expectStatus().isOk

        val query = requireNotNull(findUseCase.lastQuery)
        assertNull(query.status)
        assertNull(query.openedAfter)
        assertEquals(FindStoriesQuery.DEFAULT_LIMIT, query.limit)
    }

    @Test
    @DisplayName("상세 조회는 story와 기사·판정 기록을 함께 응답한다")
    fun getStory() {
        val story = story()
        val article = article(decision = AutoMergedLinkDecision(story.id, 0.91))
        getUseCase.detail = StoryDetail(story = story, articles = listOf(article))

        val body = webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_STORY, story.id.value)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals(story.id.value.toString(), json.get("data").get("story").get("id").asString())
        val articleRow = json.get("data").get("articles").get(0)
        assertEquals(article.newsId.value.toString(), articleRow.get("newsId").asString())
        assertEquals("AUTO_MERGED", articleRow.get("decision").get("kind").asString())
        assertEquals(0.91, articleRow.get("decision").get("similarity").asDouble())
        assertEquals(story.id, getUseCase.lastStoryId)
    }

    @Test
    @DisplayName("없는 story의 상세 조회는 404로 응답한다")
    fun getMissingStory() {
        getUseCase.failure = StoryOperationException(StoryOperationErrorCode.STORY_NOT_FOUND)

        val body = webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_STORY, UUID.randomUUID())
            .exchange()
            .expectStatus().isNotFound
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.STORY_NOT_FOUND.name, json.get("error").get("code").asString())
    }

    @Test
    @DisplayName("병합은 경로의 두 id를 명령으로 옮기고 생존자를 응답한다")
    fun mergeStories() {
        val survivor = story()
        val mergedStoryId = StoryId.newId()
        mergeUseCase.result = MergeStoryPairResult(survivor = survivor, mergedStoryId = mergedStoryId)

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_MERGE, survivor.id.value, mergedStoryId.value)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(survivor.id.value.toString(), json.get("data").get("survivorStoryId").asString())
        assertEquals(mergedStoryId.value.toString(), json.get("data").get("mergedStoryId").asString())

        val command = requireNotNull(mergeUseCase.lastCommand)
        assertEquals(survivor.id, command.targetStoryId)
        assertEquals(mergedStoryId, command.sourceStoryId)
    }

    @Test
    @DisplayName("병합 중 story가 바뀌면 409로 응답한다")
    fun mergeConflict() {
        mergeUseCase.failure = StoryOperationException(StoryOperationErrorCode.REORGANIZE_CONFLICT)

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_MERGE, UUID.randomUUID(), UUID.randomUUID())
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.STORY_CHANGED.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals(ErrorCode.STORY_CHANGED.name, JsonBody.parse(body).get("error").get("code").asString())
    }

    @Test
    @DisplayName("분리는 body의 기사 목록을 명령으로 옮기고 두 story를 응답한다")
    fun splitStory() {
        val original = story()
        val moved = article(newsId = NewsId.of(UUID.randomUUID()), storyId = StoryId.newId())
        val newStory = Story.open(moved, NOW, parentStoryId = original.id)
        splitUseCase.result = SplitStoryResult(original = original, newStory = newStory, movedArticleCount = 1)

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_SPLIT, original.id.value)
            .bodyValue(mapOf("newsIds" to listOf(moved.newsId.value.toString())))
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(original.id.value.toString(), json.get("data").get("storyId").asString())
        assertEquals(newStory.id.value.toString(), json.get("data").get("newStoryId").asString())
        assertEquals(1, json.get("data").get("movedArticleCount").asInt())

        val command = requireNotNull(splitUseCase.lastCommand)
        assertEquals(original.id, command.storyId)
        assertEquals(listOf(moved.newsId), command.newsIds)
    }

    @Test
    @DisplayName("잘못된 분리 요청은 400으로 응답한다")
    fun rejectInvalidSplit() {
        splitUseCase.failure = StoryOperationException(StoryOperationErrorCode.SPLIT_ALL_ARTICLES_REJECTED)

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_SPLIT, UUID.randomUUID())
            .bodyValue(mapOf("newsIds" to listOf(UUID.randomUUID().toString())))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals(ErrorCode.INVALID_STORY_SPLIT.name, JsonBody.parse(body).get("error").get("code").asString())
    }

    @Test
    @DisplayName("status가 enum 값이 아니면 400으로 응답한다")
    fun rejectInvalidStatus() {
        val body = webTestClient.get()
            .uri { builder ->
                builder.path(ApiPaths.V1_INTERNAL_STORIES)
                    .queryParam("status", "UNKNOWN")
                    .build()
            }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals(ErrorCode.INVALID_INTERNAL_REQUEST.name, JsonBody.parse(body).get("error").get("code").asString())
        assertEquals(0, findUseCase.invokeCount)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {

        @Bean
        fun findStoriesUseCase(): RecordingFindStoriesUseCase = RecordingFindStoriesUseCase()

        @Bean
        fun getStoryUseCase(): RecordingGetStoryUseCase = RecordingGetStoryUseCase()

        @Bean
        fun mergeStoryPairUseCase(): RecordingMergeStoryPairUseCase = RecordingMergeStoryPairUseCase()

        @Bean
        fun splitStoryUseCase(): RecordingSplitStoryUseCase = RecordingSplitStoryUseCase()
    }
}
