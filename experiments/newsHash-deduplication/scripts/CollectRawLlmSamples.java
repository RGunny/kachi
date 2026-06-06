import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CollectRawLlmSamples {

    private static final List<String> KEYWORDS = List.of("브로드컴 실적", "SPACE-X IPO");
    private static final String PROMPT_VERSION = "news-summary-v1";
    private static final int CALLS_PER_PROVIDER = 2;

    private static final List<Provider> PROVIDERS = List.of(
            new Provider("openrouter", "OPENROUTER_API_KEY", "KACHI_AI_OPENROUTER_MODEL", "openai/gpt-4o-mini",
                    env("KACHI_AI_OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
                    env("KACHI_AI_OPENROUTER_CHAT_COMPLETIONS_PATH", "/chat/completions")),
            new Provider("groq", "GROQ_API_KEY", "KACHI_AI_GROQ_MODEL", "llama-3.3-70b-versatile",
                    env("KACHI_AI_GROQ_BASE_URL", "https://api.groq.com/openai/v1"),
                    env("KACHI_AI_GROQ_CHAT_COMPLETIONS_PATH", "/chat/completions")),
            new Provider("together", "TOGETHER_API_KEY", "KACHI_AI_TOGETHER_MODEL", "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                    env("KACHI_AI_TOGETHER_BASE_URL", "https://api.together.xyz/v1"),
                    env("KACHI_AI_TOGETHER_CHAT_COMPLETIONS_PATH", "/chat/completions")),
            new Provider("cerebras", "CEREBRAS_API_KEY", "KACHI_AI_CEREBRAS_MODEL", "gpt-oss-120b",
                    env("KACHI_AI_CEREBRAS_BASE_URL", "https://api.cerebras.ai/v1"),
                    env("KACHI_AI_CEREBRAS_CHAT_COMPLETIONS_PATH", "/chat/completions")),
            new Provider("mistral", "MISTRAL_API_KEY", "KACHI_AI_MISTRAL_MODEL", "mistral-small-latest",
                    env("KACHI_AI_MISTRAL_BASE_URL", "https://api.mistral.ai/v1"),
                    env("KACHI_AI_MISTRAL_CHAT_COMPLETIONS_PATH", "/chat/completions"))
    );

    public static void main(String[] args) throws Exception {
        Path out = Arguments.parse(args).out;
        Files.createDirectories(out);
        Files.createDirectories(out.resolveSibling("results"));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        List<String> manifestItems = new ArrayList<>();

        for (Provider provider : PROVIDERS) {
            String apiKey = System.getenv(provider.apiKeyEnv);
            String model = env(provider.modelEnv, provider.defaultModel);

            if (apiKey == null || apiKey.isBlank()) {
                manifestItems.add("""
                        {"provider":"%s","model":"%s","status":"skipped","reason":"%s is not set"}"""
                        .formatted(provider.name, escape(model), provider.apiKeyEnv));
                continue;
            }

            for (int callIndex = 1; callIndex <= CALLS_PER_PROVIDER; callIndex++) {
                String requestBody = requestBody(model, callIndex);
                Instant startedAt = Instant.now();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(provider.baseUrl + provider.path))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
                String sanitizedResponse = sanitizeProviderResponse(response.body());
                String fileName = "%s-%s-call-%d.json".formatted(provider.name, safeModel(model), callIndex);
                ParsedSummary parsedSummary = parseSummary(sanitizedResponse);

                Files.writeString(
                        out.resolve(fileName),
                        rawFile(provider.name, model, callIndex, response.statusCode(), elapsedMillis, requestBody, sanitizedResponse, parsedSummary),
                        StandardCharsets.UTF_8
                );

                manifestItems.add("""
                        {"provider":"%s","model":"%s","callIndex":%d,"status":%d,"elapsedMillis":%d,"file":"raw/%s","parsed":%s}"""
                        .formatted(provider.name, escape(model), callIndex, response.statusCode(), elapsedMillis, fileName, parsedSummary.success()));
            }
        }

        Path manifest = out.resolveSibling("results").resolve("raw-manifest.json");
        Files.writeString(
                manifest,
                """
                {
                  "collectedAt": "%s",
                  "keywords": ["%s", "%s"],
                  "promptVersion": "%s",
                  "callsPerProvider": %d,
                  "providers": [
                    %s
                  ]
                }
                """.formatted(
                        Instant.now(),
                        KEYWORDS.get(0),
                        KEYWORDS.get(1),
                        PROMPT_VERSION,
                        CALLS_PER_PROVIDER,
                        String.join(",\n    ", manifestItems)
                ),
                StandardCharsets.UTF_8
        );

        System.out.println("Wrote raw LLM samples to " + out.toAbsolutePath().normalize());
        System.out.println("Wrote manifest to " + manifest.toAbsolutePath().normalize());
    }

    private static String requestBody(String model, int callIndex) {
        String userPrompt = String.join("\\n",
                "실험 호출 번호: " + callIndex,
                "키워드: " + String.join(", ", KEYWORDS),
                "뉴스:",
                "1. Broadcom quarterly earnings beat expectations as AI infrastructure demand increased.",
                "2. Analysts discussed Broadcom guidance and custom AI accelerator revenue.",
                "3. SpaceX is reportedly considering IPO timing for Starlink while SpaceX itself remains private.",
                "4. Investors are watching commercial launch cadence and satellite internet profitability."
        );

        return """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "너는 금융/기술 뉴스 요약기다. 반드시 JSON 객체만 반환한다. 형식: {\\\"title\\\":\\\"...\\\",\\\"content\\\":\\\"...\\\",\\\"sentiment\\\":\\\"POSITIVE|NEUTRAL|NEGATIVE|UNKNOWN\\\"}"
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "max_tokens": 512,
                  "temperature": 0.2
                }
                """.formatted(escape(model), escape(userPrompt));
    }

    private static String rawFile(
            String provider,
            String model,
            int callIndex,
            int status,
            long elapsedMillis,
            String requestBody,
            String rawResponse,
            ParsedSummary parsedSummary
    ) {
        return """
                {
                  "provider": "%s",
                  "model": "%s",
                  "promptVersion": "%s",
                  "callIndex": %d,
                  "keywords": ["%s", "%s"],
                  "status": %d,
                  "elapsedMillis": %d,
                  "parsedTitle": "%s",
                  "parsedContent": "%s",
                  "parsedSentiment": "%s",
                  "request": %s,
                  "rawResponseText": "%s"
                }
                """.formatted(
                provider,
                escape(model),
                PROMPT_VERSION,
                callIndex,
                KEYWORDS.get(0),
                KEYWORDS.get(1),
                status,
                elapsedMillis,
                escape(parsedSummary.title()),
                escape(parsedSummary.content()),
                escape(parsedSummary.sentiment()),
                requestBody,
                escape(rawResponse)
        );
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String safeModel(String model) {
        return model.replace("/", "_").replace(":", "_").replace(".", "_");
    }

    private static String sanitizeProviderResponse(String rawResponse) {
        return rawResponse.replaceAll("/keys/[A-Za-z0-9_-]+", "/keys/REDACTED");
    }

    private static ParsedSummary parseSummary(String rawResponse) {
        String assistantContent = extract(rawResponse, "\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"");
        if (assistantContent.isBlank()) {
            return ParsedSummary.empty();
        }

        String unescaped = unescapeJsonString(assistantContent);
        String title = extract(unescaped, "\"title\"\\s*:\\s*\"([^\"]+)\"");
        String content = extract(unescaped, "\"content\"\\s*:\\s*\"([^\"]+)\"");
        String sentiment = extract(unescaped, "\"sentiment\"\\s*:\\s*\"([^\"]+)\"");

        if (title.isBlank() || content.isBlank()) {
            return ParsedSummary.empty();
        }

        return new ParsedSummary(title, content, normalizeSentiment(sentiment));
    }

    private static String extract(String source, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(source);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String unescapeJsonString(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String normalizeSentiment(String value) {
        String normalized = value == null ? "UNKNOWN" : value.trim().toUpperCase();
        return switch (normalized) {
            case "POSITIVE", "NEUTRAL", "NEGATIVE", "UNKNOWN" -> normalized;
            default -> "UNKNOWN";
        };
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private record Provider(String name, String apiKeyEnv, String modelEnv, String defaultModel, String baseUrl, String path) {
    }

    private record ParsedSummary(String title, String content, String sentiment) {
        private static ParsedSummary empty() {
            return new ParsedSummary("", "", "UNKNOWN");
        }

        private boolean success() {
            return !title.isBlank() && !content.isBlank();
        }
    }

    private record Arguments(Path out) {
        private static Arguments parse(String[] args) {
            Path out = Path.of("experiments/newsHash-deduplication/raw");

            for (int index = 0; index < args.length; index++) {
                if ("--out".equals(args[index])) {
                    out = Path.of(args[++index]);
                    continue;
                }
                throw new IllegalArgumentException("Unknown argument: " + args[index]);
            }

            return new Arguments(out);
        }
    }
}
