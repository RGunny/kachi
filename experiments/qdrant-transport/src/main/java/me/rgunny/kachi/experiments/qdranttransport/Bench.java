package me.rgunny.kachi.experiments.qdranttransport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 두 전송 방식에 같은 부하를 주고 지연·처리량·바이트를 재는 벤치마크.
 *
 * 조건은 전송 방식 둘 × payload index 유무 둘이고 조건마다 예열 한 번 뒤 지정 횟수를 반복한다.
 * 반복마다 컬렉션을 다시 만들어 upsert가 항상 빈 컬렉션에서 시작한다.
 */
public final class Bench {

    private final Settings settings;
    private final NetCounters counters;

    public Bench(Settings settings) {
        this.settings = settings;
        this.counters = new NetCounters(settings.containerName());
    }

    public List<Measurement> run(Path dir) {
        long reference = System.currentTimeMillis();
        List<Point> points = Vectors.points(settings, reference);
        List<float[]> queries = Vectors.queries(settings, points);
        long collectedAfter = reference - settings.candidateWindowHours() * 3_600_000L;

        Jsonl.write(dir.resolve("results/environment.jsonl"),
                List.of(Environment.capture(settings, settings.qdrantImage())));

        List<Measurement> measurements = new ArrayList<>();
        for (boolean indexed : new boolean[]{false, true}) {
            try (Transport rest = new RestTransport(settings); Transport grpc = new GrpcTransport(settings)) {
                System.out.printf("payload index %s%n", indexed ? "있음" : "없음");
                warmUp(rest, indexed, points, queries, collectedAfter);
                warmUp(grpc, indexed, points, queries, collectedAfter);
                for (int repeat = 1; repeat <= settings.repeats(); repeat++) {
                    for (Transport transport : order(rest, grpc, repeat)) {
                        transport.recreateCollection(indexed);
                        measurements.add(measureUpsert(transport, indexed, repeat, points));
                        measurements.add(measureSearch(transport, indexed, repeat, queries, collectedAfter));
                        Jsonl.write(dir.resolve("results/transport.jsonl"), measurements);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("벤치마크가 실패했다", e);
            }
        }
        return measurements;
    }

    /**
     * 회차마다 두 전송 방식의 순서를 바꾼다. 항상 같은 순서면 뒤에 오는 쪽이 캐시가 덥혀진 상태에서 재게 된다.
     */
    private List<Transport> order(Transport rest, Transport grpc, int repeat) {
        return repeat % 2 == 1 ? List.of(rest, grpc) : List.of(grpc, rest);
    }

    /**
     * 첫 호출의 연결 수립과 JIT 비용을 재지 않으려고 축소한 부하를 한 번 돌린다.
     */
    private void warmUp(Transport transport, boolean indexed, List<Point> points, List<float[]> queries, long collectedAfter) {
        transport.recreateCollection(indexed);
        int warmUpPoints = Math.min(settings.batchSize() * 2, points.size());
        for (int from = 0; from < warmUpPoints; from += settings.batchSize()) {
            transport.upsert(points.subList(from, Math.min(from + settings.batchSize(), warmUpPoints)));
        }
        int warmUpQueries = Math.min(20, queries.size());
        for (int i = 0; i < warmUpQueries; i++) {
            transport.search(queries.get(i), collectedAfter, settings.topK());
        }
    }

    private Measurement measureUpsert(Transport transport, boolean indexed, int repeat, List<Point> points) {
        List<Long> latencies = new ArrayList<>();
        long requestBytes = 0;
        long responseBytes = 0;
        int upserted = 0;

        long[] before = counters.read();
        long start = System.nanoTime();
        for (int from = 0; from < points.size(); from += settings.batchSize()) {
            List<Point> batch = points.subList(from, Math.min(from + settings.batchSize(), points.size()));
            long at = System.nanoTime();
            OpResult result = transport.upsert(batch);
            latencies.add(System.nanoTime() - at);
            requestBytes += result.requestBytes();
            responseBytes += result.responseBytes();
            upserted += result.resultCount();
        }
        long wallNanos = System.nanoTime() - start;
        long[] after = counters.read();

        return measurement(transport, "upsert", indexed, repeat, latencies, upserted, wallNanos,
                requestBytes, responseBytes, before, after);
    }

    private Measurement measureSearch(Transport transport, boolean indexed, int repeat, List<float[]> queries, long collectedAfter) {
        List<Long> latencies = new ArrayList<>();
        long requestBytes = 0;
        long responseBytes = 0;
        int hits = 0;

        long[] before = counters.read();
        long start = System.nanoTime();
        for (float[] query : queries) {
            long at = System.nanoTime();
            OpResult result = transport.search(query, collectedAfter, settings.topK());
            latencies.add(System.nanoTime() - at);
            requestBytes += result.requestBytes();
            responseBytes += result.responseBytes();
            hits += result.resultCount();
        }
        long wallNanos = System.nanoTime() - start;
        long[] after = counters.read();

        return measurement(transport, "search", indexed, repeat, latencies, hits, wallNanos,
                requestBytes, responseBytes, before, after);
    }

    private Measurement measurement(Transport transport, String operation, boolean indexed, int repeat,
                                    List<Long> latencies, int results, long wallNanos,
                                    long requestBytes, long responseBytes, long[] before, long[] after) {
        double wallSeconds = wallNanos / 1_000_000_000.0;
        Measurement measurement = new Measurement(
                transport.name(),
                operation,
                indexed,
                repeat,
                latencies.size(),
                results,
                Latencies.percentileMillis(latencies, 50),
                Latencies.percentileMillis(latencies, 95),
                Latencies.percentileMillis(latencies, 99),
                wallSeconds == 0 ? 0 : latencies.size() / wallSeconds,
                requestBytes,
                responseBytes,
                after[0] - before[0],
                after[1] - before[1],
                Math.round(wallSeconds * 1000));
        System.out.printf("  %s %s repeat %d: %d회 p50 %.1fms p95 %.1fms %.1f/s 요청 %.1fMB%n",
                measurement.transport(), operation, repeat, measurement.requests(),
                measurement.p50Millis(), measurement.p95Millis(), measurement.perSecond(),
                measurement.requestBodyBytes() / 1_048_576.0);
        return measurement;
    }
}
