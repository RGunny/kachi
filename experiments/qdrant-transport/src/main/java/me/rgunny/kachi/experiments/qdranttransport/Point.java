package me.rgunny.kachi.experiments.qdranttransport;

import java.util.UUID;

/**
 * 색인에 넣는 점 하나. story-service가 넣을 것과 같은 모양이다.
 *
 * id는 기사 식별자, payload는 소속 story와 수집 시각, 언어다.
 */
public record Point(UUID id, float[] vector, String storyId, long collectedAt, String language) {
}
