package me.rgunny.kachi.experiments.qdranttransport;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 전송 방식 비교 실험의 진입점.
 *
 * <pre>
 * bench    두 전송 방식으로 부하를 주고 results/transport.jsonl과 transport.md를 만든다
 * report   기존 transport.jsonl만 읽어 transport.md를 다시 만든다
 * </pre>
 * 작업 디렉터리는 실험 루트(experiments/qdrant-transport)여야 한다. Gradle run이 그렇게 맞춘다.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: bench [--points N] [--queries N] [--repeats N] | report");
            System.exit(2);
        }
        List<String> options = Arrays.asList(args).subList(1, args.length);
        Path dir = Path.of("").toAbsolutePath();

        switch (args[0]) {
            case "bench" -> {
                Settings settings = Settings.fromEnv(
                        optionValue(options, "--points", 20_000),
                        optionValue(options, "--queries", 2_000),
                        optionValue(options, "--repeats", 3));
                Report.write(dir, new Bench(settings).run(dir));
            }
            case "report" -> Report.write(dir, Jsonl.read(dir.resolve("results/transport.jsonl"), Measurement.class));
            default -> {
                System.err.println("unknown command: " + args[0]);
                System.exit(2);
            }
        }
    }

    private static int optionValue(List<String> options, String name, int defaultValue) {
        int at = options.indexOf(name);
        if (at < 0 || at + 1 >= options.size()) {
            return defaultValue;
        }
        return Integer.parseInt(options.get(at + 1));
    }
}
