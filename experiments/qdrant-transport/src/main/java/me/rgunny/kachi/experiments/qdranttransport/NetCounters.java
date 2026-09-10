package me.rgunny.kachi.experiments.qdranttransport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 컨테이너가 실제로 주고받은 바이트를 읽는다.
 *
 * 직렬화 본문 크기는 헤더와 framing을 뺀 값이라, 전선 위의 양은 컨테이너 안의 인터페이스 카운터로 잰다.
 */
public final class NetCounters {

    private final String containerName;

    public NetCounters(String containerName) {
        this.containerName = containerName;
    }

    /**
     * eth0의 수신·송신 누적 바이트를 돌려준다.
     *
     * 카운터에 eth0 줄이 없으면 0 쌍이고 보고서에 그대로 드러난다. `docker exec` 자체가 실패하면 측정을 멈춘다.
     */
    public long[] read() {
        try {
            Process process = new ProcessBuilder("docker", "exec", containerName, "cat", "/proc/net/dev")
                    .redirectErrorStream(true)
                    .start();
            List<String> lines;
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                lines = reader.lines().toList();
            }
            process.waitFor();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("eth0:")) {
                    continue;
                }
                String[] fields = trimmed.substring(trimmed.indexOf(':') + 1).trim().split("\\s+");
                return new long[]{Long.parseLong(fields[0]), Long.parseLong(fields[8])};
            }
            return new long[]{0, 0};
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("네트워크 카운터 읽기가 중단됐다", e);
        }
    }
}
