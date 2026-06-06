import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateNewsHashDataset {

    private static final String[] KEYWORDS = {"브로드컴 실적", "SPACE-X IPO"};
    private static final String PROMPT_VERSION = "news-summary-v1";
    private static final String FROM = "2026-06-01T00:00:00Z";
    private static final String TO = "2026-06-02T00:00:00Z";

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        new GenerateNewsHashDataset().generate(arguments.rows, arguments.rawDir, arguments.outDir);
    }

    private void generate(int rows, Path rawDir, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        List<RawSeed> seeds = loadSeeds(rawDir);

        try (
                BufferedWriter first = Files.newBufferedWriter(outDir.resolve("first-run.jsonl"), StandardCharsets.UTF_8);
                BufferedWriter second = Files.newBufferedWriter(outDir.resolve("second-run.jsonl"), StandardCharsets.UTF_8);
                BufferedWriter added = Files.newBufferedWriter(outDir.resolve("added-news-run.jsonl"), StandardCharsets.UTF_8)
        ) {
            for (int index = 0; index < rows; index++) {
                first.write(document(index, seeds.get(index % seeds.size()), "first", false));
                first.write("\n");

                second.write(document(index, seeds.get(index % seeds.size()), "second", false));
                second.write("\n");

                added.write(document(index, seeds.get(index % seeds.size()), "added", true));
                added.write("\n");
            }
        }

        System.out.println("Generated rows=" + rows + " into " + outDir.toAbsolutePath().normalize());
        System.out.println("Raw seed count=" + seeds.size());
    }

    private String document(int index, RawSeed seed, String runName, boolean includeAddedNews) {
        String keyword = KEYWORDS[index % KEYWORDS.length];
        int logicalGroup = index;
        List<String> sourceNewsIds = sourceNewsIds(logicalGroup, includeAddedNews);
        String sourceNewsIdsCanonical = sourceNewsIdsCanonical(sourceNewsIds);
        String newsHash = newsHash(keyword, FROM, TO, sourceNewsIds);

        return """
                {"_id":{"$uuid":"%s"},"keyword":"%s","summaryFrom":{"$date":"%s"},"summaryTo":{"$date":"%s"},"sourceNewsIds":%s,"sourceNewsIdsCanonical":"%s","newsHash":"%s","title":"%s #%d","content":"%s dummy-sequence=%d","sentiment":"%s","provider":"%s","model":"%s","promptVersion":"%s","inputTokens":%d,"outputTokens":%d,"createdAt":{"$date":"%s"}}
                """.formatted(
                UUID.nameUUIDFromBytes((seed.provider + seed.model + index + runName).getBytes(StandardCharsets.UTF_8)),
                escape(keyword),
                FROM,
                TO,
                jsonStringArray(sourceNewsIds),
                sourceNewsIdsCanonical,
                newsHash,
                escape(seed.title),
                logicalGroup,
                escape(seed.content),
                index,
                seed.sentiment,
                seed.provider,
                escape(seed.model),
                PROMPT_VERSION,
                seed.inputTokens + (index % 50),
                seed.outputTokens + (index % 30),
                Instant.parse("2026-06-02T00:00:00Z").plusSeconds(index)
        ).strip();
    }

    private String sourceNewsIdsCanonical(List<String> sourceNewsIds) {
        return sourceNewsIds.stream()
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private List<String> sourceNewsIds(int logicalGroup, boolean includeAddedNews) {
        List<String> ids = new ArrayList<>();
        ids.add(uuid(logicalGroup));
        ids.add(uuid(logicalGroup + 1));
        ids.add(uuid(logicalGroup + 2));
        if (includeAddedNews) {
            ids.add(uuid(logicalGroup + 999_999));
        }
        return ids;
    }

    private String uuid(int value) {
        return UUID.nameUUIDFromBytes(("news-" + value).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String newsHash(String keyword, String from, String to, List<String> sourceNewsIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = keyword + "|" + from + "|" + to + "|" + sourceNewsIdsCanonical(sourceNewsIds);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<RawSeed> loadSeeds(Path rawDir) throws IOException {
        if (!Files.exists(rawDir)) {
            return fallbackSeeds();
        }

        List<RawSeed> seeds = new ArrayList<>();
        try (var paths = Files.list(rawDir)) {
            List<Path> files = paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            for (Path file : files) {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                String title = extract(raw, "\"parsedTitle\"\\s*:\\s*\"([^\"]*)\"", "");
                String content = extract(raw, "\"parsedContent\"\\s*:\\s*\"([^\"]*)\"", "");
                if (title.isBlank() || content.isBlank()) {
                    continue;
                }

                seeds.add(new RawSeed(
                        extract(raw, "\"provider\"\\s*:\\s*\"([^\"]+)\"", "unknown"),
                        extract(raw, "\"model\"\\s*:\\s*\"([^\"]+)\"", "unknown-model"),
                        title,
                        shorten(content),
                        extract(raw, "\"parsedSentiment\"\\s*:\\s*\"([^\"]*)\"", "UNKNOWN"),
                        100 + seeds.size(),
                        50 + seeds.size()
                ));
            }
        }

        return seeds.isEmpty() ? fallbackSeeds() : seeds;
    }

    private List<RawSeed> fallbackSeeds() {
        return List.of(
                new RawSeed("openrouter", "openai/gpt-4o-mini", "fallback openrouter", "fallback raw response", "UNKNOWN", 100, 50),
                new RawSeed("groq", "llama-3.3-70b-versatile", "fallback groq", "fallback raw response", "UNKNOWN", 101, 51),
                new RawSeed("together", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "fallback together", "fallback raw response", "UNKNOWN", 102, 52),
                new RawSeed("cerebras", "gpt-oss-120b", "fallback cerebras", "fallback raw response", "UNKNOWN", 103, 53),
                new RawSeed("mistral", "mistral-small-latest", "fallback mistral", "fallback raw response", "UNKNOWN", 104, 54)
        );
    }

    private String extract(String source, String regex, String defaultValue) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(source);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private String shorten(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String jsonStringArray(List<String> values) {
        return "[" + values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private record RawSeed(
            String provider,
            String model,
            String title,
            String content,
            String sentiment,
            int inputTokens,
            int outputTokens
    ) {
    }

    private record Arguments(int rows, Path rawDir, Path outDir) {
        private static Arguments parse(String[] args) {
            int rows = 100_000;
            Path rawDir = Path.of("experiments/newsHash-deduplication/raw");
            Path outDir = Path.of("experiments/newsHash-deduplication/generated/100000");

            for (int index = 0; index < args.length; index++) {
                if ("--rows".equals(args[index])) {
                    rows = Integer.parseInt(args[++index]);
                    continue;
                }
                if ("--raw-dir".equals(args[index])) {
                    rawDir = Path.of(args[++index]);
                    continue;
                }
                if ("--out".equals(args[index])) {
                    outDir = Path.of(args[++index]);
                    continue;
                }
                throw new IllegalArgumentException("Unknown argument: " + args[index]);
            }

            return new Arguments(rows, rawDir, outDir);
        }
    }
}
