package me.rgunny.kachi.user.fixture

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * user-service 테스트가 공유하는 고정 시간.
 *
 * 가입·로그인·토큰 만료의 선후 관계를 테스트마다 다시 세우면 기준 시각이 조금씩 어긋난다.
 * 시나리오 고유 시각은 이 상수의 상대값([NOW].plus 등)으로 표현한다.
 */
object UserTestFixture {
    val NOW: Instant = Instant.parse("2026-05-20T00:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
}
