package me.rgunny.kachi.experiments.qdranttransport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 측정 기록을 표 하나로 옮기는 보고서 생성기.
 *
 * 행은 전송 방식 × 작업 × payload index × 반복이고, 마지막에 반복 중간값 요약을 붙인다.
 */
public final class Report {

    private Report() {
    }

    public static void write(Path dir, List<Measurement> measurements) {
        List<Measurement> rows = measurements.stream()
                .sorted(Comparator.comparing(Measurement::operation)
                        .thenComparing(Measurement::payloadIndexed)
                        .thenComparing(Measurement::transport)
                        .thenComparing(Measurement::repeat))
                .toList();

        StringBuilder out = new StringBuilder();
        out.append("# Qdrant 전송 방식 비교 (REST 대 gRPC)\n\n");
        out.append("`./scripts/run.sh bench`가 만든 `results/transport.jsonl`에서 나온 표다.\n");
        out.append("같은 호스트의 컨테이너를 재므로 네트워크 지연 차이는 담기지 않는다.\n\n");
        out.append("payload index 조건은 운영 컬렉션과 같은 셋(`storyId` keyword, `collectedAt` integer, `language` keyword)을 만든 상태다.\n");
        out.append("질의가 쓰는 것은 `collectedAt` 범위 index 하나이므로 측정된 검색 차이는 그 index의 효과다.\n");
        out.append("나머지 둘은 운영 컬렉션을 그대로 재현하려고 같이 만든다.\n\n");

        appendEnvironment(out, dir);

        out.append("## 측정값\n\n");
        out.append("| 작업 | payload index | 전송 | 회차 | 요청 수 | 결과 수 | p50(ms) | p95(ms) | p99(ms) | 초당 | 요청 본문(MB) | 응답 본문(MB) | 컨테이너 수신(MB) | 컨테이너 송신(MB) | 소요(s) |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (Measurement row : rows) {
            out.append(String.format("| %s | %s | %s | %d | %d | %d | %.2f | %.2f | %.2f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f |%n",
                    row.operation(),
                    row.payloadIndexed() ? "있음" : "없음",
                    row.transport(),
                    row.repeat(),
                    row.requests(),
                    row.results(),
                    row.p50Millis(),
                    row.p95Millis(),
                    row.p99Millis(),
                    row.perSecond(),
                    row.requestBodyBytes() / 1_048_576.0,
                    row.responseBodyBytes() / 1_048_576.0,
                    row.containerRxBytes() / 1_048_576.0,
                    row.containerTxBytes() / 1_048_576.0,
                    row.wallMillis() / 1000.0));
        }

        out.append("\n## 반복 중간값\n\n");
        out.append("| 작업 | payload index | 전송 | p50(ms) | p95(ms) | 초당 | 요청 본문(MB) |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        rows.stream()
                .map(row -> row.operation() + "|" + row.payloadIndexed() + "|" + row.transport())
                .distinct()
                .forEach(key -> {
                    List<Measurement> group = rows.stream()
                            .filter(row -> (row.operation() + "|" + row.payloadIndexed() + "|" + row.transport()).equals(key))
                            .toList();
                    Measurement first = group.getFirst();
                    out.append(String.format("| %s | %s | %s | %.2f | %.2f | %.1f | %.1f |%n",
                            first.operation(),
                            first.payloadIndexed() ? "있음" : "없음",
                            first.transport(),
                            median(group.stream().map(Measurement::p50Millis).toList()),
                            median(group.stream().map(Measurement::p95Millis).toList()),
                            median(group.stream().map(Measurement::perSecond).toList()),
                            median(group.stream().map(row -> row.requestBodyBytes() / 1_048_576.0).toList())));
                });

        out.append("\n바이트는 두 지표다. 요청·응답 본문은 직렬화된 논리 크기이고 HTTP 헤더와 HTTP/2 framing이 빠져 있다.\n");
        out.append("컨테이너 수신·송신은 Qdrant 컨테이너 `eth0`의 누적 카운터 차이라 프로토콜 오버헤드가 들어 있다.\n");

        try {
            Path path = dir.resolve("results/transport.md");
            Files.createDirectories(path.getParent());
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
            System.out.println("wrote " + path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 측정이 난 환경을 표로 붙인다. 기록이 없으면 절을 만들지 않는다.
     */
    private static void appendEnvironment(StringBuilder out, Path dir) {
        Path path = dir.resolve("results/environment.jsonl");
        if (!Files.exists(path)) {
            return;
        }
        List<Environment> captured = Jsonl.read(path, Environment.class);
        if (captured.isEmpty()) {
            return;
        }
        Environment environment = captured.getFirst();
        out.append("## 실행 환경\n\n");
        out.append("| 항목 | 값 |\n| --- | --- |\n");
        out.append(String.format("| 측정 시각 | %s |%n", environment.measuredAt()));
        out.append(String.format("| Qdrant | %s (버전 %s) |%n", environment.qdrantImage(), environment.qdrantVersion()));
        out.append(String.format("| Docker 서버 | %s, CPU %d, 메모리 %.1fGiB |%n",
                environment.dockerVersion(), environment.dockerCpus(), environment.dockerMemoryBytes() / 1_073_741_824.0));
        out.append(String.format("| JDK | %s (%s), 프로세서 %d |%n",
                environment.jdkVersion(), environment.jvmArch(), environment.hostProcessors()));
        out.append(String.format("| 부하 | 점 %d개, 배치 %d, 질의 %d개, top-%d, 반복 %d회 |%n%n",
                environment.points(), environment.batchSize(), environment.queries(), environment.topK(), environment.repeats()));
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get(sorted.size() / 2);
    }
}
