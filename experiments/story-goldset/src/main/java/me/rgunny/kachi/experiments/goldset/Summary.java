package me.rgunny.kachi.experiments.goldset;

import java.util.List;

/**
 * ai-service의 news_summaries 문서에서 쌍 샘플링에 필요한 값만 옮긴 요약.
 *
 * 요약 자체는 골드셋에 들어가지 않는다. 어떤 기사들이 한 요약에 묶였는지, 어떤 기사가 여러 요약에 인용됐는지가
 * "같은 사건일 가능성이 높은 쌍"과 "다른 사건일 가능성이 높은 쌍"을 고르는 단서다.
 */
public record Summary(
        String id,
        String keyword,
        List<String> sourceNewsIds,
        String title,
        String createdAt
) {
}
