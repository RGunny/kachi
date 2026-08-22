package me.rgunny.kachi.ai.support

import me.rgunny.kachi.ai.fixture.AiTestFixture
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 테스트가 시간을 원하는 만큼 진행시킬 수 있는 clock.
 *
 * cooldown 만료처럼 "얼마나 지났는가"로 갈리는 동작은 고정 clock으로 확인할 수 없다.
 * 실제 대기 없이 경계를 검증하려고 둔다.
 */
class MutableClock(
    private var current: Instant = AiTestFixture.NOW,
    private val zone: ZoneId = ZoneOffset.UTC
) : Clock() {

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
