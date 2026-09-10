package me.rgunny.kachi.experiments.qdranttransport;

/**
 * 한 조건에서 잰 값 한 줄.
 *
 * 조건은 전송 방식, 작업, payload index 유무, 반복 회차다. 표와 보고서는 이 줄만 읽는다.
 */
public record Measurement(
        String transport,
        String operation,
        boolean payloadIndexed,
        int repeat,
        int requests,
        int results,
        double p50Millis,
        double p95Millis,
        double p99Millis,
        double perSecond,
        long requestBodyBytes,
        long responseBodyBytes,
        long containerRxBytes,
        long containerTxBytes,
        long wallMillis
) {
}
