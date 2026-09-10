package me.rgunny.kachi.experiments.qdranttransport;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 측정이 난 실행 환경. 같은 수치를 다시 내려면 이 값들이 같아야 한다.
 *
 * 호스트가 아니라 Docker가 컨테이너에 주는 CPU·메모리를 적는다. 부하를 받는 쪽의 자원이 그것이다.
 */
public record Environment(
        String measuredAt,
        String jdkVersion,
        String jvmArch,
        int hostProcessors,
        String dockerVersion,
        int dockerCpus,
        long dockerMemoryBytes,
        String qdrantVersion,
        String qdrantImage,
        int points,
        int queries,
        int batchSize,
        int topK,
        int repeats
) {

    public static Environment capture(Settings settings, String qdrantImage) {
        return new Environment(
                Instant.now().toString(),
                System.getProperty("java.version"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                command("docker", "version", "--format", "{{.Server.Version}}"),
                Integer.parseInt(commandOrDefault("0", "docker", "info", "--format", "{{.NCPU}}")),
                Long.parseLong(commandOrDefault("0", "docker", "info", "--format", "{{.MemTotal}}")),
                qdrantVersion(settings),
                qdrantImage,
                settings.points(),
                settings.queries(),
                settings.batchSize(),
                settings.topK(),
                settings.repeats());
    }

    private static String qdrantVersion(Settings settings) {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.restBaseUrl() + "/")).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = Jsonl.MAPPER.readTree(response.body());
            return body.path("version").asString("unknown");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant 버전 조회가 중단됐다", e);
        }
    }

    private static String commandOrDefault(String defaultValue, String... command) {
        String value = command(command);
        return value.isBlank() ? defaultValue : value;
    }

    private static String command(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            List<String> lines;
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                lines = reader.lines().toList();
            }
            process.waitFor();
            return lines.isEmpty() ? "" : lines.getFirst().trim();
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(command[0] + " 실행이 중단됐다", e);
        }
    }
}
