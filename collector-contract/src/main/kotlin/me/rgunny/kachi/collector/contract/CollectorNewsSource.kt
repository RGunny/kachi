package me.rgunny.kachi.collector.contract

/**
 * 기사를 준 provider. 이벤트 계약의 값이며 collector 도메인 enum과 값마다 짝을 짓는다.
 */
enum class CollectorNewsSource {
    GOOGLE,
    NAVER,
    FINNHUB
}
