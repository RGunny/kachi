package me.rgunny.kachi.experiments.goldset;

/**
 * 골드셋의 한 행. 기사 둘과 그 둘이 같은 사건인지에 대한 LLM 초안과 사람의 확정 라벨이다.
 *
 * stratum은 쌍을 어떻게 뽑았는지(S1~S5)이며 라벨이 아니다. label이 null이면 아직 사람이 보지 않은 것이다.
 */
public record ArticlePair(
        String pairId,
        String stratum,
        Article a,
        Article b,
        Draft draft,
        Boolean label
) {

    /**
     * LLM이 낸 초안. same은 같은 사건인지, reason은 그 근거다.
     */
    public record Draft(boolean same, String reason) {
    }

    public ArticlePair withDraft(Draft newDraft) {
        return new ArticlePair(pairId, stratum, a, b, newDraft, label);
    }

    public boolean bothHaveExcerpt() {
        return a.hasExcerpt() && b.hasExcerpt();
    }
}
