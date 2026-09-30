package me.rgunny.kachi.story.domain.index

/** 벡터 색인 호출 실패의 책임 위치. */
enum class CandidateIndexFailureAttribution {
    /**
     * 이 요청의 입력.
     *
     * 색인이 거부한 벡터·필터·식별자.
     */
    INPUT,

    /**
     * 우리가 띄운 색인 서버.
     *
     * 부재, timeout, 과부하, 서버 오류, 컬렉션 부재, 인증.
     */
    INDEX
}
