package me.rgunny.kachi.experiments.qdranttransport;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 시드 고정 부하 데이터 생성기.
 *
 * 같은 시드로 두 전송 방식에 같은 점과 같은 질의를 준다. 전송 방식 말고 다른 변수가 없어야 비교가 성립한다.
 */
public final class Vectors {

    private Vectors() {
    }

    /**
     * 단위 벡터 점 목록을 만든다. 수집 시각은 기준 시각에서 창 전체에 고르게 퍼지고 언어는 ko 7 대 en 3이다.
     */
    public static List<Point> points(Settings settings, long referenceMillis) {
        Random random = new Random(settings.seed());
        long spreadMillis = settings.spreadHours() * 3_600_000L;
        List<Point> points = new ArrayList<>(settings.points());
        for (int i = 0; i < settings.points(); i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            String storyId = storyId(settings, i);
            long collectedAt = referenceMillis - spreadMillis + (long) ((double) i / settings.points() * spreadMillis);
            String language = i % 10 < 7 ? "ko" : "en";
            points.add(new Point(id, unitVector(random, settings.dimension()), storyId, collectedAt, language));
        }
        return points;
    }

    /**
     * 넣은 벡터에 작은 잡음을 더한 질의를 만든다. 결과가 비면 검색 비용을 재는 뜻이 없어진다.
     */
    public static List<float[]> queries(Settings settings, List<Point> points) {
        Random random = new Random(settings.seed() + 1);
        List<float[]> queries = new ArrayList<>(settings.queries());
        int stride = Math.max(1, points.size() / settings.queries());
        for (int i = 0; i < settings.queries(); i++) {
            float[] source = points.get((i * stride) % points.size()).vector();
            float[] query = new float[source.length];
            for (int d = 0; d < source.length; d++) {
                query[d] = source[d] + (float) (random.nextGaussian() * 0.02);
            }
            queries.add(normalize(query));
        }
        return queries;
    }

    private static String storyId(Settings settings, int index) {
        int story = index / settings.pointsPerStory();
        return new UUID(settings.seed(), story).toString();
    }

    private static float[] unitVector(Random random, int dimension) {
        float[] values = new float[dimension];
        for (int d = 0; d < dimension; d++) {
            values[d] = (float) random.nextGaussian();
        }
        return normalize(values);
    }

    private static float[] normalize(float[] values) {
        double sum = 0;
        for (float value : values) {
            sum += (double) value * value;
        }
        float norm = (float) Math.sqrt(sum);
        for (int d = 0; d < values.length; d++) {
            values[d] = values[d] / norm;
        }
        return values;
    }
}
