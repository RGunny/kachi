package me.rgunny.kachi.experiments.goldset;

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
 * 한 줄에 JSON 하나인 파일을 읽고 쓴다.
 *
 * 골드셋·점수·Mongo export가 전부 이 형식이라 diff로 행 단위 변화를 볼 수 있다.
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
