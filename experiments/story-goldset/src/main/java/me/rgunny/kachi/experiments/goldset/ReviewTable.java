package me.rgunny.kachi.experiments.goldset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사람이 라벨을 확정하는 검토표를 만들고, 채워진 검토표를 goldset/pairs.jsonl에 되돌려 넣는다.
 *
 * 검토표는 코사인이 높은 쌍부터 놓는다. 임계값이 갈리는 곳이 거기라 먼저 보는 것이 이득이고,
 * 코사인이 낮은 쌍은 어떤 θ에서도 같은 story로 묶이지 않아 라벨이 재현율 분모에만 영향을 준다.
 */
public final class ReviewTable {

    private static final String SAME = "같음";
    private static final String DIFFERENT = "다름";

    private ReviewTable() {
    }

    public static void write(Path dir) {
        List<ArticlePair> pairs = Jsonl.read(dir.resolve("goldset/pairs.jsonl"), ArticlePair.class);
        Map<String, ScorePairs.Score> scores = new HashMap<>();
        Path scoresPath = dir.resolve("results/scores.jsonl");
        if (Files.exists(scoresPath)) {
            scores = Jsonl.read(scoresPath, ScorePairs.Score.class).stream()
                    .collect(Collectors.toMap(ScorePairs.Score::pairId, Function.identity()));
        }
        Map<String, ScorePairs.Score> byId = scores;

        List<ArticlePair> ordered = new ArrayList<>(pairs);
        ordered.sort(Comparator.comparingDouble((ArticlePair p) -> byId.containsKey(p.pairId()) ? byId.get(p.pairId()).cosine() : -1).reversed());

        StringBuilder md = new StringBuilder();
        md.append("# 골드셋 검토표\n\n");
        md.append("초안은 Ollama가 낸 것이며 정답이 아니다. **확정** 칸에 `같음` 또는 `다름`을 적고 `./scripts/run.sh apply`로 옮긴다.\n");
        md.append("같은 사건이란 같은 시점에 일어난 같은 일이다. 속보·후속 반응·분석·다른 매체의 전재는 같은 사건이고, 같은 회사·인물의 다른 일은 다른 사건이다.\n");
        md.append("코사인이 높은 순이다. 층: S1 같은 요약 · S2 다른 요약이지만 인용 겹침 · S3 같은 키워드 6h 이상 간격 · S4 다른 키워드 · S5 발췌문 있는 최근 수집\n\n");
        md.append("| pairId | 층 | 코사인 | 판정기 | 기사 A | 기사 B | 초안 | 근거 | 확정 |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (ArticlePair pair : ordered) {
            ScorePairs.Score score = byId.get(pair.pairId());
            String draft = pair.draft() == null ? "" : (pair.draft().same() ? SAME : DIFFERENT);
            String reason = pair.draft() == null ? "" : pair.draft().reason();
            String label = pair.label() == null ? "" : (pair.label() ? SAME : DIFFERENT);
            md.append("| ").append(pair.pairId())
                    .append(" | ").append(pair.stratum())
                    .append(" | ").append(score == null ? "" : String.format(Locale.ROOT, "%.2f", score.cosine()))
                    .append(" | ").append(score == null ? "" : String.format(Locale.ROOT, "%.2f", score.judge()))
                    .append(" | ").append(cell(pair.a()))
                    .append(" | ").append(cell(pair.b()))
                    .append(" | ").append(draft)
                    .append(" | ").append(escape(reason))
                    .append(" | ").append(label)
                    .append(" |\n");
        }
        Path out = dir.resolve("results/review.md");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("review table -> " + out);
    }

    /**
     * 검토표의 확정 칸을 읽어 label을 채운다. 비어 있는 행은 건드리지 않고, 값이 `같음`·`다름`이 아니면 멈춘다.
     */
    public static void apply(Path dir) {
        Path reviewPath = dir.resolve("results/review.md");
        Map<String, Boolean> labels = new HashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(reviewPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (String line : lines) {
            if (!line.startsWith("| S")) {
                continue;
            }
            List<String> cells = splitRow(line);
            String pairId = cells.get(0).strip();
            String decided = cells.get(cells.size() - 1).strip();
            if (decided.isEmpty()) {
                continue;
            }
            switch (decided) {
                case SAME, "O", "o", "same", "true" -> labels.put(pairId, true);
                case DIFFERENT, "X", "x", "diff", "false" -> labels.put(pairId, false);
                default -> throw new IllegalArgumentException(pairId + " 확정 칸을 읽을 수 없습니다: '" + decided + "' (같음|다름)");
            }
        }

        Path pairsPath = dir.resolve("goldset/pairs.jsonl");
        List<ArticlePair> pairs = Jsonl.read(pairsPath, ArticlePair.class);
        List<ArticlePair> updated = new ArrayList<>();
        int changed = 0;
        for (ArticlePair pair : pairs) {
            Boolean label = labels.get(pair.pairId());
            if (label == null) {
                updated.add(pair);
                continue;
            }
            if (!label.equals(pair.label())) {
                changed++;
            }
            updated.add(new ArticlePair(pair.pairId(), pair.stratum(), pair.a(), pair.b(), pair.draft(), label));
        }
        Jsonl.write(pairsPath, updated);
        long labeledTotal = updated.stream().filter(p -> p.label() != null).count();
        long agree = updated.stream().filter(p -> p.label() != null && p.draft() != null && p.label().equals(p.draft().same())).count();
        System.out.printf("applied %d labels (%d changed). labeled %d/%d, agree with draft %d%n",
                labels.size(), changed, labeledTotal, updated.size(), agree);
    }

    /**
     * 셀 안의 `\|`를 구분자로 보지 않도록 자른다.
     */
    private static List<String> splitRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String body = line.substring(1, line.length() - 1);
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length() && body.charAt(i + 1) == '|') {
                current.append('|');
                i++;
            } else if (c == '|') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static String cell(Article article) {
        String text = escape(article.title());
        if (article.hasExcerpt()) {
            text += "<br>" + escape(article.excerpt());
        }
        return text;
    }

    private static String escape(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
