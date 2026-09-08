package me.rgunny.kachi.experiments.goldset;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 로컬 Ollama로 각 쌍에 "같은 사건인가" 초안을 붙인다. 끝나면 사람이 확정할 검토표(review.md)를 다시 만든다.
 *
 * 초안은 정답이 아니다. 사람이 review.md를 보고 goldset/pairs.jsonl의 label을 채워야 골드셋이 된다.
 * 응답이 오지 않은 쌍은 draft를 비워 두고 마지막에 목록으로 알린다. 다시 실행하면 빈 것만 채운다.
 */
public final class DraftLabels {

    private static final String SYSTEM_PROMPT = """
            두 뉴스 기사가 같은 사건을 다루는지 판단한다.
            같은 사건이란 같은 시점에 일어난 같은 일(발표, 계약, 사고, 발언, 판결 등)을 말한다.
            같은 사건의 속보, 후속 반응, 분석, 다른 매체의 전재는 모두 같은 사건이다.
            같은 회사나 인물이 나와도 다른 일이면 다른 사건이다. 같은 주제의 일반 동향 기사도 다른 사건이다.
            제목과 발췌문만으로 판단하고, 확신이 없으면 false로 답한다.
            JSON 한 개로만 답한다: {"same": true 또는 false, "reason": "한 문장"}
            """;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;
    private final String model;

    private DraftLabels(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public static void run(Path dir) {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen3.8:27b");
        new DraftLabels(baseUrl, model).draft(dir);
    }

    private void draft(Path dir) {
        Path pairsPath = dir.resolve("goldset/pairs.jsonl");
        List<ArticlePair> pairs = new ArrayList<>(Jsonl.read(pairsPath, ArticlePair.class));
        List<String> failed = new ArrayList<>();
        int done = 0;

        for (int i = 0; i < pairs.size(); i++) {
            ArticlePair pair = pairs.get(i);
            if (pair.draft() != null) {
                continue;
            }
            ArticlePair.Draft draft = ask(pair);
            if (draft == null) {
                failed.add(pair.pairId());
                continue;
            }
            pairs.set(i, pair.withDraft(draft));
            done++;
            System.out.printf("%s %s %s%n", pair.pairId(), draft.same() ? "SAME" : "DIFF", draft.reason());
            // 1. 27B 모델은 쌍당 수 초가 걸린다. 중간에 끊겨도 다시 시작할 수 있게 10건마다 저장한다.
            if (done % 10 == 0) {
                Jsonl.write(pairsPath, pairs);
            }
        }
        Jsonl.write(pairsPath, pairs);
        ReviewTable.write(dir);

        System.out.println("drafted " + done + ", failed " + failed.size());
        if (!failed.isEmpty()) {
            System.out.println("failed pairIds: " + String.join(", ", failed) + " (다시 실행하면 이것만 채운다)");
        }
    }

    private ArticlePair.Draft ask(ArticlePair pair) {
        String user = "기사 A\n제목: " + pair.a().title() + "\n발췌문: " + excerptOrNone(pair.a())
                + "\n\n기사 B\n제목: " + pair.b().title() + "\n발췌문: " + excerptOrNone(pair.b());
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "think", false,
                "options", Map.of("temperature", 0),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", user)
                )
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Jsonl.MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        // 2. 응답이 JSON이 아니거나 필드가 빠지면 같은 요청을 세 번까지 다시 보낸다. temperature 0이라도 형식은 흔들릴 수 있다.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    System.err.println(pair.pairId() + " attempt " + attempt + " http " + response.statusCode() + ": " + response.body());
                    continue;
                }
                JsonNode content = Jsonl.MAPPER.readTree(response.body()).path("message").path("content");
                JsonNode answer = Jsonl.MAPPER.readTree(content.asString());
                if (!answer.has("same")) {
                    System.err.println(pair.pairId() + " attempt " + attempt + " no 'same' field: " + content.asString());
                    continue;
                }
                return new ArticlePair.Draft(answer.get("same").asBoolean(), answer.path("reason").asString(""));
            } catch (IOException e) {
                System.err.println(pair.pairId() + " attempt " + attempt + " failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (RuntimeException e) {
                System.err.println(pair.pairId() + " attempt " + attempt + " bad response: " + e.getMessage());
            }
        }
        return null;
    }

    private static String excerptOrNone(Article article) {
        return article.hasExcerpt() ? article.excerpt() : "(없음)";
    }
}
