package me.rgunny.kachi.experiments.goldset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mongo export(요약·기사)에서 라벨링할 기사 쌍 250개를 다섯 층으로 뽑는다.
 *
 * 층은 "이 쌍을 왜 골랐나"이지 정답이 아니다. 같은 요약에 묶였다고 같은 사건이라는 보장은 없고,
 * 다른 키워드라고 다른 사건이라는 보장도 없다. 정답은 사람이 붙인다.
 * 시드를 고정해 같은 export에서 같은 쌍이 나온다.
 */
public final class ExtractPairs {

    private static final long SEED = 20260908L;
    private static final int SAME_SUMMARY = 80;
    private static final int SHARED_CITATION = 50;
    private static final int SAME_KEYWORD_APART = 60;
    private static final int CROSS_KEYWORD = 30;
    private static final int RECENT_WITH_EXCERPT = 30;
    private static final Duration FAR_APART = Duration.ofHours(6);
    private static final Instant RECENT_FROM = Instant.parse("2026-09-08T00:00:00Z");
    private static final double NEAR_IDENTICAL = 0.9;
    private static final double MIN_OVERLAP = 0.2;
    private static final Pattern PUBLISHER_SUFFIX = Pattern.compile("(\\s+[-|]\\s+[^-|\\[\\]]{1,30})+$");
    private static final Pattern BRACKET_TAG = Pattern.compile("\\[[^\\]]{1,20}\\]");

    private final Random random = new Random(SEED);
    private final Set<String> takenKeys = new HashSet<>();
    private final List<ArticlePair> pairs = new ArrayList<>();
    private final Map<String, Integer> sequenceByStratum = new TreeMap<>();

    private final Map<String, Article> articles;
    private final List<Summary> summaries;
    private final Map<String, List<String>> citedBySummary;

    private ExtractPairs(Map<String, Article> articles, List<Summary> summaries) {
        this.articles = articles;
        this.summaries = summaries;
        this.citedBySummary = summaries.stream().collect(Collectors.toMap(
                Summary::id,
                summary -> summary.sourceNewsIds().stream().filter(articles::containsKey).toList()
        ));
    }

    public static void run(Path dir, boolean force) {
        Path out = dir.resolve("goldset/pairs.jsonl");
        if (Files.exists(out) && !force) {
            throw new IllegalStateException(
                    out + " 가 이미 있습니다. 사람 라벨이 들어 있을 수 있어 덮어쓰지 않습니다. 다시 뽑으려면 --force");
        }

        Map<String, Article> articles = Jsonl.read(dir.resolve("generated/news.jsonl"), Article.class).stream()
                .collect(Collectors.toMap(Article::newsId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<Summary> summaries = Jsonl.read(dir.resolve("generated/summaries.jsonl"), Summary.class).stream()
                .sorted(Comparator.comparing(Summary::createdAt).thenComparing(Summary::id))
                .toList();

        ExtractPairs extractor = new ExtractPairs(articles, summaries);
        extractor.sameSummary();
        extractor.sharedCitation();
        extractor.sameKeywordFarApart();
        extractor.crossKeyword();
        extractor.recentWithExcerpt();

        Jsonl.write(out, extractor.pairs);
        System.out.println("pairs written: " + extractor.pairs.size() + " -> " + out);
        extractor.sequenceByStratum.forEach((stratum, n) -> System.out.println("  " + stratum + ": " + n));
    }

    /**
     * S1. 한 요약에 같이 인용된 기사 둘. 요약이 사건을 잘 묶었다면 같은 사건이지만, 여러 사건을 한 요약에 담은 경우가 섞인다.
     */
    private void sameSummary() {
        List<Summary> candidates = summaries.stream()
                .filter(summary -> citedBySummary.get(summary.id()).size() >= 2)
                .toList();
        roundRobin("S1", SAME_SUMMARY, candidates, summary -> {
            List<String> cited = citedBySummary.get(summary.id());
            return randomDistinctPair(cited);
        });
    }

    /**
     * S2. 두 요약 이상에 인용된 기사와, 그중 한 요약에만 있는 다른 기사. 같은 사건이 요약 여러 개로 갈라진 흔적을 따라간다.
     */
    private void sharedCitation() {
        Map<String, List<Summary>> summariesByArticle = new TreeMap<>();
        for (Summary summary : summaries) {
            for (String newsId : citedBySummary.get(summary.id())) {
                summariesByArticle.computeIfAbsent(newsId, k -> new ArrayList<>()).add(summary);
            }
        }
        List<Map.Entry<String, List<Summary>>> shared = summariesByArticle.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .toList();
        roundRobin("S2", SHARED_CITATION, shared, entry -> {
            String x = entry.getKey();
            List<Summary> owners = entry.getValue();
            Summary first = owners.get(random.nextInt(owners.size()));
            Summary second = owners.get(random.nextInt(owners.size()));
            if (first == second) {
                return null;
            }
            Set<String> inFirst = new HashSet<>(citedBySummary.get(first.id()));
            List<String> onlyInSecond = citedBySummary.get(second.id()).stream()
                    .filter(id -> !inFirst.contains(id) && !id.equals(x))
                    .toList();
            if (onlyInSecond.isEmpty()) {
                return null;
            }
            return new String[]{x, onlyInSecond.get(random.nextInt(onlyInSecond.size()))};
        });
    }

    /**
     * S3. 같은 키워드지만 인용이 겹치지 않고 6시간 이상 떨어진 두 요약에서 하나씩. 대체로 다른 사건이며 같은 주제의 오탐을 잰다.
     */
    private void sameKeywordFarApart() {
        List<Summary[]> summaryPairs = new ArrayList<>();
        for (int i = 0; i < summaries.size(); i++) {
            for (int j = i + 1; j < summaries.size(); j++) {
                Summary first = summaries.get(i);
                Summary second = summaries.get(j);
                if (!first.keyword().equals(second.keyword()) || sharesCitation(first, second) || !farApart(first, second)) {
                    continue;
                }
                if (citedBySummary.get(first.id()).isEmpty() || citedBySummary.get(second.id()).isEmpty()) {
                    continue;
                }
                summaryPairs.add(new Summary[]{first, second});
            }
        }
        shuffle(summaryPairs);
        roundRobin("S3", SAME_KEYWORD_APART, summaryPairs, this::oneFromEach);
    }

    /**
     * S4. 키워드가 다른 두 요약에서 하나씩. 대체로 다른 사건이지만 트럼프·이란처럼 사건이 겹치는 키워드가 있다.
     */
    private void crossKeyword() {
        List<Summary[]> summaryPairs = new ArrayList<>();
        for (int i = 0; i < summaries.size(); i++) {
            for (int j = i + 1; j < summaries.size(); j++) {
                Summary first = summaries.get(i);
                Summary second = summaries.get(j);
                if (first.keyword().equals(second.keyword())) {
                    continue;
                }
                if (citedBySummary.get(first.id()).isEmpty() || citedBySummary.get(second.id()).isEmpty()) {
                    continue;
                }
                summaryPairs.add(new Summary[]{first, second});
            }
        }
        shuffle(summaryPairs);
        roundRobin("S4", CROSS_KEYWORD, summaryPairs, this::oneFromEach);
    }

    /**
     * S5. 발췌문이 실제로 있는 최근 수집분. 제목 토큰이 많이 겹치는 상위 절반과 무작위 절반.
     * 요약에 인용된 기사는 전부 제목만 있어, 발췌문이 붙은 입력의 거동은 이 층으로만 본다.
     */
    private void recentWithExcerpt() {
        List<Article> recent = articles.values().stream()
                .filter(Article::hasExcerpt)
                .filter(article -> !Instant.parse(article.collectedAt()).isBefore(RECENT_FROM))
                .sorted(Comparator.comparing(Article::newsId))
                .toList();
        if (recent.size() < 2) {
            System.out.println("S5: 발췌문이 있는 최근 기사가 부족합니다: " + recent.size());
            return;
        }

        Map<String, Set<String>> tokens = new HashMap<>();
        for (Article article : recent) {
            tokens.put(article.newsId(), tokenize(normalizeTitle(article.title())));
        }
        record Scored(Article a, Article b, double jaccard) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < recent.size(); i++) {
            for (int j = i + 1; j < recent.size(); j++) {
                Article a = recent.get(i);
                Article b = recent.get(j);
                // 제목이 사실상 같은 쌍(따옴표 모양·매체명 접미만 다름)은 URL만 다른 재수집이라 물을 것이 없다.
                // 토큰이 많이 겹치되 같지는 않은 제목을 고른다. 판정이 갈릴 만한 회색 구간을 보려는 층이다.
                double overlap = jaccard(tokens.get(a.newsId()), tokens.get(b.newsId()));
                if (overlap >= NEAR_IDENTICAL || overlap < MIN_OVERLAP) {
                    continue;
                }
                scored.add(new Scored(a, b, overlap));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::jaccard).reversed());

        // 같은 기사가 상위 쌍 여럿에 반복해서 들어가면 한 사건만 보게 되므로, 상위 절반에서는 기사당 한 번만 쓴다.
        int half = RECENT_WITH_EXCERPT / 2;
        Set<String> usedInTop = new HashSet<>();
        for (Scored candidate : scored) {
            if (countOf("S5") >= half) {
                break;
            }
            if (usedInTop.contains(candidate.a().newsId()) || usedInTop.contains(candidate.b().newsId())) {
                continue;
            }
            if (add("S5", candidate.a().newsId(), candidate.b().newsId())) {
                usedInTop.add(candidate.a().newsId());
                usedInTop.add(candidate.b().newsId());
            }
        }
        int attempts = 0;
        while (countOf("S5") < RECENT_WITH_EXCERPT && attempts++ < 10_000) {
            Article a = recent.get(random.nextInt(recent.size()));
            Article b = recent.get(random.nextInt(recent.size()));
            add("S5", a.newsId(), b.newsId());
        }
    }

    private <T> void roundRobin(String stratum, int target, List<T> sources, Function<T, String[]> picker) {
        if (sources.isEmpty()) {
            System.out.println(stratum + ": 후보가 없습니다");
            return;
        }
        int idleRounds = 0;
        while (countOf(stratum) < target && idleRounds < 20) {
            boolean added = false;
            for (T source : sources) {
                if (countOf(stratum) >= target) {
                    break;
                }
                String[] picked = picker.apply(source);
                if (picked != null && add(stratum, picked[0], picked[1])) {
                    added = true;
                }
            }
            idleRounds = added ? 0 : idleRounds + 1;
        }
        if (countOf(stratum) < target) {
            System.out.println(stratum + ": 목표 " + target + " 중 " + countOf(stratum) + "만 뽑았습니다");
        }
    }

    private String[] oneFromEach(Summary[] summaryPair) {
        List<String> first = citedBySummary.get(summaryPair[0].id());
        List<String> second = citedBySummary.get(summaryPair[1].id());
        return new String[]{first.get(random.nextInt(first.size())), second.get(random.nextInt(second.size()))};
    }

    private String[] randomDistinctPair(List<String> ids) {
        if (ids.size() < 2) {
            return null;
        }
        int i = random.nextInt(ids.size());
        int j = random.nextInt(ids.size() - 1);
        if (j >= i) {
            j++;
        }
        return new String[]{ids.get(i), ids.get(j)};
    }

    private boolean add(String stratum, String newsIdA, String newsIdB) {
        if (newsIdA.equals(newsIdB)) {
            return false;
        }
        String key = newsIdA.compareTo(newsIdB) < 0 ? newsIdA + "|" + newsIdB : newsIdB + "|" + newsIdA;
        if (!takenKeys.add(key)) {
            return false;
        }
        int sequence = sequenceByStratum.merge(stratum, 1, Integer::sum);
        String pairId = String.format("%s-%03d", stratum, sequence);
        pairs.add(new ArticlePair(pairId, stratum, articles.get(newsIdA), articles.get(newsIdB), null, null));
        return true;
    }

    private int countOf(String stratum) {
        return sequenceByStratum.getOrDefault(stratum, 0);
    }

    private boolean sharesCitation(Summary first, Summary second) {
        Set<String> inFirst = new HashSet<>(citedBySummary.get(first.id()));
        return citedBySummary.get(second.id()).stream().anyMatch(inFirst::contains);
    }

    private static boolean farApart(Summary first, Summary second) {
        Duration gap = Duration.between(Instant.parse(first.createdAt()), Instant.parse(second.createdAt())).abs();
        return gap.compareTo(FAR_APART) >= 0;
    }

    private <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    /**
     * 매체 접미(" - 연합뉴스")와 코너 태그("[자막뉴스]")를 뗀다. 같은 기사의 Google·Naver 사본이 이 부분만 다르다.
     */
    static String normalizeTitle(String title) {
        String stripped = BRACKET_TAG.matcher(title).replaceAll(" ");
        stripped = PUBLISHER_SUFFIX.matcher(stripped.strip()).replaceAll("");
        return stripped.strip();
    }

    private static Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toSet());
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        long common = a.stream().filter(b::contains).count();
        return (double) common / (a.size() + b.size() - common);
    }
}
