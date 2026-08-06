package me.rgunny.kachi.ai.domain.quarantine

/**
 * 키워드 격리 상태.
 *
 * 연속 실패 횟수는 격리 전부터 실행을 넘겨가며 누적해야 하므로, 격리되지 않은 추적 상태를 따로 둔다.
 */
enum class KeywordQuarantineStatus {
    /** 연속 실패를 누적 중이지만 아직 임계치에 닿지 않은 상태 */
    TRACKING,

    /** 임계치에 도달해 요약 대상과 watermark 전진 판단에서 제외된 상태 */
    QUARANTINED,

    /** 운영자가 격리를 해제한 상태 */
    RELEASED
}
