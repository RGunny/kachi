package me.rgunny.kachi.experiments.goldset;

import java.util.List;

/**
 * collector의 news 문서에서 골드셋에 필요한 값만 옮긴 기사.
 *
 * 2026-09-08 이관으로 옛 문서는 excerpt에 제목이 복사돼 있다. 그 경우 발췌문이 없는 것으로 보고
 * 임베딩 입력을 제목만으로 만들어, 운영에서 provider가 실제로 주는 입력과 같은 모양을 유지한다.
 */
public record Article(
        String newsId,
        String title,
        String excerpt,
        String source,
        String language,
        String collectedAt,
        List<String> matchedKeywords
) {

    public boolean hasExcerpt() {
        return excerpt != null && !excerpt.isBlank() && !excerpt.equals(title);
    }

    /**
     * 조립 규칙 ②의 임베딩 입력이다. 제목과 발췌문을 줄바꿈으로 잇는다.
     */
    public String embeddingText() {
        return hasExcerpt() ? title + "\n" + excerpt : title;
    }
}
