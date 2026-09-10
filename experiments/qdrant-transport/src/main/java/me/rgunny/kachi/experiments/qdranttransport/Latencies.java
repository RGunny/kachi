package me.rgunny.kachi.experiments.qdranttransport;

import java.util.List;

/**
 * 지연 표본의 분위값 계산기.
 *
 * 표본을 정렬해 최근접 순위로 고른다. 표본이 수백에서 수천이라 보간까지 갈 필요가 없다.
 */
public final class Latencies {

    private Latencies() {
    }

    public static double percentileMillis(List<Long> nanos, double percentile) {
        if (nanos.isEmpty()) {
            return 0;
        }
        List<Long> sorted = nanos.stream().sorted().toList();
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        int index = Math.min(Math.max(rank, 0), sorted.size() - 1);
        return sorted.get(index) / 1_000_000.0;
    }
}
