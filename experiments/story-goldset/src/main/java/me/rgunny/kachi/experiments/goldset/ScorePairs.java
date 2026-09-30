package me.rgunny.kachi.experiments.goldset;

import ai.djl.huggingface.translator.TextClassificationTranslatorFactory;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.util.StringPair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 골드셋 전 쌍에 임베딩 코사인 유사도와 cross-encoder 판정 점수를 계산한다.
 *
 * 모델은 DJL 모델 저장소(mlrepo.djl.ai)의 TorchScript 변환본을 zip 주소로 직접 받는다. zoo 색인이 이 모델들을
 * 목록에 올리지 않아 `djl://` 주소로는 찾지 못하기 때문이며, 번역기와 pooling은 저장소 metadata의 값을 그대로 넘긴다.
 * TEI와 같은 가중치라 fp32 수치가 같고, 이 Mac(arm64)에서 네이티브로 돈다.
 * 어느 모델·아티팩트로 낸 점수인지 행마다 남겨 나중에 다른 모델과 비교할 수 있게 한다.
 */
public final class ScorePairs {

    private static final int EMBED_BATCH = 16;
    private static final int JUDGE_BATCH = 8;

    /**
     * 점수 한 행. cosine은 -1~1, judge는 sigmoid를 거친 0~1이다.
     */
    public record Score(String pairId, double cosine, double judge, String embeddingModel, String judgeModel) {
    }

    /**
     * 임베딩 모델의 정체. 저장소 zip 주소, 결과에 남길 코드, pooling, e5처럼 입력 앞에 붙일 접두어를 갖는다.
     */
    enum Embedding {
        BGE_M3(REPO + "text_embedding/ai/djl/huggingface/pytorch/BAAI/bge-m3/0.0.1/bge-m3.zip", "BGE_M3", "cls", ""),
        E5_LARGE(REPO + "text_embedding/ai/djl/huggingface/pytorch/intfloat/multilingual-e5-large/0.0.1/multilingual-e5-large.zip",
                "MULTILINGUAL_E5_LARGE", "mean", "query: ");

        final String url;
        final String code;
        final String pooling;
        final String prefix;

        Embedding(String url, String code, String pooling, String prefix) {
            this.url = url;
            this.code = code;
            this.pooling = pooling;
            this.prefix = prefix;
        }

        static Embedding of(String name) {
            return switch (name) {
                case "bge" -> BGE_M3;
                case "e5" -> E5_LARGE;
                default -> throw new IllegalArgumentException("모르는 임베딩 모델: " + name + " (bge | e5)");
            };
        }
    }

    private static final String REPO = "https://mlrepo.djl.ai/model/nlp/";
    private static final String JUDGE_URL = REPO + "text_classification/ai/djl/huggingface/pytorch/BAAI/bge-reranker-v2-m3/0.0.1/bge-reranker-v2-m3.zip";
    private static final String MAX_LENGTH = "512";
    private static final String JUDGE_CODE = "BGE_RERANKER_V2_M3";

    private ScorePairs() {
    }

    public static void run(Path dir, String embeddingName) throws Exception {
        Embedding embedding = Embedding.of(embeddingName);
        List<ArticlePair> pairs = Jsonl.read(dir.resolve("goldset/pairs.jsonl"), ArticlePair.class);

        Map<String, float[]> vectors = embed(embedding, pairs);
        Map<String, Float> judged = judge(pairs);

        List<Score> scores = new ArrayList<>();
        for (ArticlePair pair : pairs) {
            double cosine = cosine(vectors.get(pair.a().newsId()), vectors.get(pair.b().newsId()));
            scores.add(new Score(pair.pairId(), cosine, judged.get(pair.pairId()), embedding.code, JUDGE_CODE));
        }
        String fileName = embedding == Embedding.BGE_M3 ? "scores.jsonl" : "scores-" + embeddingName + ".jsonl";
        Path out = dir.resolve("results").resolve(fileName);
        Jsonl.write(out, scores);
        System.out.println("scores written: " + scores.size() + " -> " + out);
    }

    private static Map<String, float[]> embed(Embedding embedding, List<ArticlePair> pairs) throws Exception {
        Map<String, String> texts = new LinkedHashMap<>();
        for (ArticlePair pair : pairs) {
            texts.putIfAbsent(pair.a().newsId(), embedding.prefix + pair.a().embeddingText());
            texts.putIfAbsent(pair.b().newsId(), embedding.prefix + pair.b().embeddingText());
        }
        List<String> ids = new ArrayList<>(texts.keySet());
        List<String> inputs = new ArrayList<>(texts.values());

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(embedding.url)
                .optEngine("PyTorch")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .optArgument("pooling", embedding.pooling)
                .optArgument("normalize", "true")
                .optArgument("padding", "true")
                .optArgument("truncation", "true")
                .optArgument("maxLength", MAX_LENGTH)
                .optProgress(new ProgressBar())
                .build();

        Map<String, float[]> vectors = new LinkedHashMap<>();
        try (ZooModel<String, float[]> model = criteria.loadModel();
             Predictor<String, float[]> predictor = model.newPredictor()) {
            // 1. 같은 기사가 여러 쌍에 나오므로 기사 단위로 한 번만 임베딩한다.
            for (int from = 0; from < inputs.size(); from += EMBED_BATCH) {
                int to = Math.min(from + EMBED_BATCH, inputs.size());
                List<float[]> batch = predictor.batchPredict(inputs.subList(from, to));
                for (int i = 0; i < batch.size(); i++) {
                    vectors.put(ids.get(from + i), batch.get(i));
                }
                System.out.printf("embedded %d/%d%n", to, inputs.size());
            }

            // 2. 자기 자신과의 코사인이 1이어야 정규화와 파이프라인이 맞은 것이다.
            float[] first = vectors.get(ids.get(0));
            System.out.printf("sanity: self cosine = %.4f (dim %d)%n", cosine(first, first), first.length);
        }
        return vectors;
    }

    private static Map<String, Float> judge(List<ArticlePair> pairs) throws Exception {
        Criteria<StringPair, float[]> criteria = Criteria.builder()
                .setTypes(StringPair.class, float[].class)
                .optModelUrls(JUDGE_URL)
                .optEngine("PyTorch")
                .optTranslatorFactory(new TextClassificationTranslatorFactory())
                .optArgument("reranking", "true")
                .optArgument("padding", "true")
                .optArgument("truncation", "true")
                .optArgument("maxLength", MAX_LENGTH)
                .optProgress(new ProgressBar())
                .build();

        Map<String, Float> judged = new LinkedHashMap<>();
        try (ZooModel<StringPair, float[]> model = criteria.loadModel();
             Predictor<StringPair, float[]> predictor = model.newPredictor()) {
            for (int from = 0; from < pairs.size(); from += JUDGE_BATCH) {
                int to = Math.min(from + JUDGE_BATCH, pairs.size());
                List<StringPair> inputs = pairs.subList(from, to).stream()
                        .map(pair -> new StringPair(pair.a().embeddingText(), pair.b().embeddingText()))
                        .toList();
                List<float[]> batch = predictor.batchPredict(inputs);
                for (int i = 0; i < batch.size(); i++) {
                    judged.put(pairs.get(from + i).pairId(), batch.get(i)[0]);
                }
                System.out.printf("judged %d/%d%n", to, pairs.size());
            }

            // 3. 같은 텍스트 둘은 판정기가 거의 1을 줘야 한다.
            String self = pairs.get(0).a().embeddingText();
            System.out.printf("sanity: self judge = %.4f%n", predictor.predict(new StringPair(self, self))[0]);
        }
        return judged;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
