package me.rgunny.kachi.experiments.qdranttransport;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 한 줄에 JSON 하나인 측정 기록 파일을 읽고 쓴다.
 *
 * 보고서를 다시 만들 때 벤치마크를 다시 돌리지 않으려고 원자료를 이 형식으로 남긴다.
 */
public final class Jsonl {

    public static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private Jsonl() {
    }

    public static <T> List<T> read(Path path, Class<T> type) {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines
                    .filter(line -> !line.isBlank())
                    .map(line -> MAPPER.readValue(line, type))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(Path path, List<?> rows) {
        try {
            Files.createDirectories(path.getParent());
            StringBuilder out = new StringBuilder();
            for (Object row : rows) {
                out.append(MAPPER.writeValueAsString(row)).append('\n');
            }
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
