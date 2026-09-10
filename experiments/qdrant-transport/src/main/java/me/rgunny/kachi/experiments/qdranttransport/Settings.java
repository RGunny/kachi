package me.rgunny.kachi.experiments.qdranttransport;

/**
 * 실험 대상 Qdrant의 주소와 부하 크기.
 *
 * 포트는 실험 compose와 같은 환경변수에서 읽는다. 부하 크기는 확정 스펙 값이 기본이고 인자로 줄일 수 있다.
 */
public record Settings(
        String host,
        int restPort,
        int grpcPort,
        String containerName,
        String qdrantImage,
        String collectionName,
        int dimension,
        int points,
        int pointsPerStory,
        int batchSize,
        int queries,
        int topK,
        int repeats,
        long candidateWindowHours,
        long spreadHours,
        long seed
) {

    public static Settings fromEnv(int points, int queries, int repeats) {
        return new Settings(
                envOrDefault("QDRANT_TRANSPORT_EXPERIMENT_HOST", "localhost"),
                Integer.parseInt(envOrDefault("QDRANT_TRANSPORT_EXPERIMENT_PORT", "6343")),
                Integer.parseInt(envOrDefault("QDRANT_TRANSPORT_EXPERIMENT_GRPC_PORT", "6344")),
                envOrDefault("QDRANT_TRANSPORT_EXPERIMENT_CONTAINER", "kachi-experiment-qdrant-transport"),
                "qdrant/qdrant:v1.19.1",
                "transport-bench",
                1024,
                points,
                10,
                100,
                queries,
                10,
                repeats,
                72,
                96,
                20260909L
        );
    }

    public String restBaseUrl() {
        return "http://" + host + ":" + restPort;
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
