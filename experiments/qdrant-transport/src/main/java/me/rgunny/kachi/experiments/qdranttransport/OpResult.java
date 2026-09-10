package me.rgunny.kachi.experiments.qdranttransport;

/**
 * 요청 한 번의 결과와 직렬화 본문 크기.
 *
 * 바이트는 HTTP 헤더와 HTTP/2 framing을 뺀 본문의 논리 크기다. 실제 전송량은 컨테이너 카운터로 따로 잰다.
 */
public record OpResult(int resultCount, long requestBytes, long responseBytes) {
}
