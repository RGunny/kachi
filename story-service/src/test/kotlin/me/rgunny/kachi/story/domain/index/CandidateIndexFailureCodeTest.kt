package me.rgunny.kachi.story.domain.index

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("CandidateIndexFailureCode")
class CandidateIndexFailureCodeTest {

    @ParameterizedTest
    @CsvSource(
        "INDEX_UNAVAILABLE, INDEX, true",
        "INDEX_TIMEOUT, INDEX, true",
        "INDEX_OVERLOADED, INDEX, true",
        "INDEX_SERVER_ERROR, INDEX, true",
        "INDEX_COLLECTION_MISSING, INDEX, false",
        "INDEX_REQUEST_REJECTED, INPUT, false",
        "INDEX_PRECONDITION_FAILED, INDEX, false",
        "INDEX_UNAUTHORIZED, INDEX, false",
        "INDEX_UNKNOWN_ERROR, INDEX, true"
    )
    @DisplayName("상수마다 책임·지속 두 축이 표대로다")
    fun axesPerCode(code: CandidateIndexFailureCode, attribution: CandidateIndexFailureAttribution, transient: Boolean) {
        assertEquals(attribution, code.attribution)
        assertEquals(transient, code.transient)
        assertEquals(code.name, code.code)
    }

    @Test
    @DisplayName("실패 값은 코드의 두 축을 그대로 보인다")
    fun failureDerivesAxes() {
        val failure = CandidateIndexFailure(code = CandidateIndexFailureCode.INDEX_REQUEST_REJECTED, grpcStatus = "INVALID_ARGUMENT")

        assertEquals(CandidateIndexFailureAttribution.INPUT, failure.attribution)
        assertEquals(false, failure.transient)
        assertEquals(CandidateIndexFailureCode.INDEX_REQUEST_REJECTED.defaultMessage, failure.message)
    }

    @Test
    @DisplayName("빈 메시지는 거부한다")
    fun rejectBlankMessage() {
        assertFailsWith<IllegalArgumentException> {
            CandidateIndexFailure(CandidateIndexFailureCode.INDEX_TIMEOUT, message = " ")
        }
    }
}
