package me.rgunny.kachi.collector.fixture

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * collector-service 테스트가 공유하는 고정 시간.
 *
 * 테스트마다 다른 시각을 세우면 수집 시각과 조회 구간의 선후 관계를 테스트별로 다시 읽어야 한다.
 * 시나리오 고유 시각은 이 상수의 상대값([NOW].plus 등)으로 표현한다.
 */
object CollectorTestFixture {
    val NOW: Instant = Instant.parse("2026-05-30T00:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
}
