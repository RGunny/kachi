package me.rgunny.kachi.experiments.goldset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 라벨이 붙은 쌍에 조립 규칙 ③을 θ 조합마다 적용해 정밀도·재현율을 재고, 정밀도 우선으로 값을 고른다.
 *
 * 오탐(다른 사건을 합침)이 미탐(같은 사건을 나눔)보다 사용자에게 나쁘다. 합쳐진 story는 요약이 섞이고
 * 되돌리기 어렵지만, 나뉜 story는 주기 병합이 다시 합칠 수 있다. 그래서 정밀도 하한을 두고 그 안에서 재현율을 본다.
 */
public final class EvaluateThresholds {

    private static final double PRECISION_FLOOR = 0.95;
    private static final double[] COSINE_GRID = grid(0.40, 0.95, 0.05);
    private static final double[] JUDGE_GRID = grid(0.30, 0.95, 0.05);

    /**
     * 라벨과 점수가 합쳐진 한 쌍.
     */
    record Labeled(ArticlePair pair, boolean same, double cosine, double judge) {
    }

    /**
     * θ 조합 하나의 성적. gray는 판정기를 불러야 하는 회색 구간 비율이다.
     */
    record Result(double high, double low, double judgeThreshold, int tp, int fp, int fn, int tn, double gray) {

        double precision() {
            return tp + fp == 0 ? 1.0 : (double) tp / (tp + fp);
        }

        double recall() {
            return tp + fn == 0 ? 1.0 : (double) tp / (tp + fn);
        }

        double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0 : 2 * p * r / (p + r);
        }
    }

    private EvaluateThresholds() {
    }

    /**
     * 조립 규칙 ③ 그대로다. story-service의 판정 코드가 이 함수와 같아야 골드셋이 회귀 테스트로 쓰인다.
     */
    static boolean decide(double cosine, double judge, double high, double low, double judgeThreshold) {
        if (cosine >= high) {
            return true;
        }
        if (cosine >= low) {
            return judge >= judgeThreshold;
        }
        return false;
    }

    public static void run(Path dir, boolean useDraft, String embeddingName) {
        String scoresFile = embeddingName.equals("bge") ? "scores.jsonl" : "scores-" + embeddingName + ".jsonl";
        Map<String, ScorePairs.Score> scores = Jsonl.read(dir.resolve("results").resolve(scoresFile), ScorePairs.Score.class)
                .stream().collect(Collectors.toMap(ScorePairs.Score::pairId, Function.identity()));
        List<ArticlePair> pairs = Jsonl.read(dir.resolve("goldset/pairs.jsonl"), ArticlePair.class);

        List<Labeled> labeled = new ArrayList<>();
        for (ArticlePair pair : pairs) {
            Boolean label = pair.label();
            if (label == null && useDraft && pair.draft() != null) {
                label = pair.draft().same();
            }
            ScorePairs.Score score = scores.get(pair.pairId());
            if (label == null || score == null) {
                continue;
            }
            labeled.add(new Labeled(pair, label, score.cosine(), score.judge()));
        }
        if (labeled.isEmpty()) {
            System.out.println("라벨이 있는 쌍이 없습니다. review.md를 보고 label을 채우거나 --use-draft로 초안을 씁니다.");
            return;
        }

        List<Result> results = sweep(labeled);
        Result chosen = choose(results);
        String labelSource = useDraft ? "LLM 초안 (사람 확정 전, 참고용)" : "사람 확정";
        String report = report(labeled, results, chosen, labelSource, embeddingName);

        Path out = dir.resolve("results").resolve(embeddingName.equals("bge") ? "thresholds.md" : "thresholds-" + embeddingName + ".md");
        try {
            Files.writeString(out, report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println(report.lines().limit(30).collect(Collectors.joining("\n")));
        System.out.println("... -> " + out);
    }

    private static List<Result> sweep(List<Labeled> labeled) {
        List<Result> results = new ArrayList<>();
        for (double high : COSINE_GRID) {
            for (double low : COSINE_GRID) {
                if (low > high + 1e-9) {
                    continue;
                }
                for (double judge : JUDGE_GRID) {
                    results.add(evaluate(labeled, high, low, judge, l -> true));
                }
            }
        }
        return results;
    }

    private static Result evaluate(List<Labeled> labeled, double high, double low, double judge, Predicate<Labeled> filter) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        int gray = 0;
        int total = 0;
        for (Labeled row : labeled) {
            if (!filter.test(row)) {
                continue;
            }
            total++;
            if (row.cosine() >= low && row.cosine() < high) {
                gray++;
            }
            boolean predicted = decide(row.cosine(), row.judge(), high, low, judge);
            if (predicted && row.same()) {
                tp++;
            } else if (predicted) {
                fp++;
            } else if (row.same()) {
                fn++;
            } else {
                tn++;
            }
        }
        return new Result(high, low, judge, tp, fp, fn, tn, total == 0 ? 0 : (double) gray / total);
    }

    /**
     * 정밀도 하한을 넘는 것 중 재현율이 가장 높은 조합. 같으면 판정기 호출이 적은 쪽(회색 구간이 좁은 쪽)이다.
     * 하한을 넘는 조합이 없으면 F1 최대를 고르고 보고서에 그렇게 적는다.
     */
    private static Result choose(List<Result> results) {
        Comparator<Result> byRecallThenGray = Comparator.comparingDouble(Result::recall).reversed()
                .thenComparingDouble(Result::gray)
                .thenComparing(Comparator.comparingDouble(Result::high).reversed());
        return results.stream()
                .filter(r -> r.precision() >= PRECISION_FLOOR)
                .min(byRecallThenGray)
                .orElseGet(() -> results.stream().max(Comparator.comparingDouble(Result::f1)).orElseThrow());
    }

    private static String report(List<Labeled> labeled, List<Result> results, Result chosen, String labelSource, String embeddingName) {
        long positives = labeled.stream().filter(Labeled::same).count();
        StringBuilder md = new StringBuilder();
        md.append("# 임계값 측정 결과 (").append(embeddingName).append(")\n\n");
        md.append("- 라벨 출처: ").append(labelSource).append('\n');
        md.append("- 쌍: ").append(labeled.size()).append(" (같은 사건 ").append(positives)
                .append(", 다른 사건 ").append(labeled.size() - positives).append(")\n");
        md.append("- 선택 기준: 정밀도 ≥ ").append(PRECISION_FLOOR).append(" 중 재현율 최대");
        if (chosen.precision() < PRECISION_FLOOR) {
            md.append(" — **하한을 넘는 조합이 없어 F1 최대로 골랐다**");
        }
        md.append("\n\n");

        md.append("## 선택 값\n\n");
        md.append("| θ_high | θ_low | θ_judge | 정밀도 | 재현율 | F1 | 회색 구간 | TP | FP | FN | TN |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        md.append(row(chosen)).append('\n');

        md.append("## 층별 (선택 값 기준)\n\n");
        md.append("| 층 | 쌍 | 같은 사건 | TP | FP | FN | TN | 정밀도 | 재현율 |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        Map<String, List<Labeled>> byStratum = labeled.stream().collect(Collectors.groupingBy(l -> l.pair().stratum(), TreeMap::new, Collectors.toList()));
        for (Map.Entry<String, List<Labeled>> entry : byStratum.entrySet()) {
            Result r = evaluate(labeled, chosen.high(), chosen.low(), chosen.judgeThreshold(), l -> l.pair().stratum().equals(entry.getKey()));
            long same = entry.getValue().stream().filter(Labeled::same).count();
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d | %d | %.3f | %.3f |%n",
                    entry.getKey(), entry.getValue().size(), same, r.tp(), r.fp(), r.fn(), r.tn(), r.precision(), r.recall()));
        }
        md.append('\n');

        md.append("## 입력 종류별 (선택 값 기준)\n\n");
        md.append("| 입력 | 쌍 | 정밀도 | 재현율 |\n| --- | --- | --- | --- |\n");
        Result withExcerpt = evaluate(labeled, chosen.high(), chosen.low(), chosen.judgeThreshold(), l -> l.pair().bothHaveExcerpt());
        Result titleOnly = evaluate(labeled, chosen.high(), chosen.low(), chosen.judgeThreshold(), l -> !l.pair().bothHaveExcerpt());
        md.append(String.format(Locale.ROOT, "| 제목+발췌문 | %d | %.3f | %.3f |%n", withExcerpt.tp() + withExcerpt.fp() + withExcerpt.fn() + withExcerpt.tn(), withExcerpt.precision(), withExcerpt.recall()));
        md.append(String.format(Locale.ROOT, "| 제목만 | %d | %.3f | %.3f |%n%n", titleOnly.tp() + titleOnly.fp() + titleOnly.fn() + titleOnly.tn(), titleOnly.precision(), titleOnly.recall()));

        md.append("## 단일 신호 참고\n\n");
        md.append("| 신호 | 임계값 | 정밀도 | 재현율 | F1 |\n| --- | --- | --- | --- | --- |\n");
        Result cosineOnly = bestSingle(labeled, COSINE_GRID, t -> evaluate(labeled, t, t, 1.0, l -> true));
        Result judgeOnly = bestSingle(labeled, JUDGE_GRID, t -> evaluate(labeled, 1.01, -1.0, t, l -> true));
        md.append(String.format(Locale.ROOT, "| 코사인만 | %.2f | %.3f | %.3f | %.3f |%n", cosineOnly.high(), cosineOnly.precision(), cosineOnly.recall(), cosineOnly.f1()));
        md.append(String.format(Locale.ROOT, "| 판정기만 | %.2f | %.3f | %.3f | %.3f |%n%n", judgeOnly.judgeThreshold(), judgeOnly.precision(), judgeOnly.recall(), judgeOnly.f1()));

        md.append("## 상위 20 조합 (정밀도 하한 안에서 재현율 순)\n\n");
        md.append("| θ_high | θ_low | θ_judge | 정밀도 | 재현율 | F1 | 회색 구간 | TP | FP | FN | TN |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        results.stream()
                .filter(r -> r.precision() >= PRECISION_FLOOR)
                .sorted(Comparator.comparingDouble(Result::recall).reversed().thenComparingDouble(Result::gray))
                .limit(20)
                .forEach(r -> md.append(row(r)));
        md.append('\n');

        md.append("## 점수 분포\n\n");
        md.append("| 라벨 | 쌍 | 코사인 최소 | 코사인 중앙 | 코사인 최대 | 판정기 최소 | 판정기 중앙 | 판정기 최대 |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        md.append(distribution("같은 사건", labeled.stream().filter(Labeled::same).toList()));
        md.append(distribution("다른 사건", labeled.stream().filter(l -> !l.same()).toList()));
        md.append('\n');

        md.append("## 오답 (선택 값 기준)\n\n");
        md.append("| pairId | 종류 | 코사인 | 판정기 | 기사 A | 기사 B |\n| --- | --- | --- | --- | --- | --- |\n");
        for (Labeled row : labeled) {
            boolean predicted = decide(row.cosine(), row.judge(), chosen.high(), chosen.low(), chosen.judgeThreshold());
            if (predicted == row.same()) {
                continue;
            }
            md.append(String.format(Locale.ROOT, "| %s | %s | %.3f | %.3f | %s | %s |%n",
                    row.pair().pairId(), predicted ? "오탐(합침)" : "미탐(나눔)", row.cosine(), row.judge(),
                    escape(row.pair().a().title()), escape(row.pair().b().title())));
        }
        return md.toString();
    }

    private static Result bestSingle(List<Labeled> labeled, double[] grid, Function<Double, Result> evaluator) {
        Result best = null;
        for (double t : grid) {
            Result r = evaluator.apply(t);
            if (best == null || r.f1() > best.f1()) {
                best = r;
            }
        }
        return best;
    }

    private static String distribution(String name, List<Labeled> rows) {
        if (rows.isEmpty()) {
            return "| " + name + " | 0 | | | | | | |\n";
        }
        double[] cosines = rows.stream().mapToDouble(Labeled::cosine).sorted().toArray();
        double[] judges = rows.stream().mapToDouble(Labeled::judge).sorted().toArray();
        return String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f |%n", name, rows.size(),
                cosines[0], cosines[cosines.length / 2], cosines[cosines.length - 1],
                judges[0], judges[judges.length / 2], judges[judges.length - 1]);
    }

    private static String row(Result r) {
        return String.format(Locale.ROOT, "| %.2f | %.2f | %.2f | %.3f | %.3f | %.3f | %.0f%% | %d | %d | %d | %d |%n",
                r.high(), r.low(), r.judgeThreshold(), r.precision(), r.recall(), r.f1(), r.gray() * 100, r.tp(), r.fp(), r.fn(), r.tn());
    }

    private static String escape(String text) {
        return text.replace("|", "\\|");
    }

    private static double[] grid(double from, double to, double step) {
        int n = (int) Math.round((to - from) / step) + 1;
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = Math.round((from + i * step) * 100) / 100.0;
        }
        return values;
    }
}
