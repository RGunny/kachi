package me.rgunny.kachi.story.application.exception

/**
 * story 조회·병합·분리 운영 유스케이스의 에러 코드.
 */
enum class StoryOperationErrorCode(
    override val code: String,
    override val message: String
) : StoryErrorCode {
    STORY_NOT_FOUND(
        code = "STORY_NOT_FOUND",
        message = "story를 찾을 수 없습니다"
    ),
    STORY_NOT_OPEN(
        code = "STORY_NOT_OPEN",
        message = "OPEN story만 병합·분리할 수 있습니다"
    ),
    MERGE_INCOMPATIBLE(
        code = "MERGE_INCOMPATIBLE",
        message = "두 story를 합칠 수 없습니다"
    ),
    SPLIT_ARTICLES_REQUIRED(
        code = "SPLIT_ARTICLES_REQUIRED",
        message = "분리할 기사 목록이 비어 있습니다"
    ),
    SPLIT_ARTICLES_NOT_IN_STORY(
        code = "SPLIT_ARTICLES_NOT_IN_STORY",
        message = "그 story에 속하지 않은 기사가 있습니다"
    ),
    SPLIT_ALL_ARTICLES_REJECTED(
        code = "SPLIT_ALL_ARTICLES_REJECTED",
        message = "기사 전체 분리는 허용하지 않습니다"
    ),
    REORGANIZE_CONFLICT(
        code = "REORGANIZE_CONFLICT",
        message = "story가 그 사이 바뀌어 재편성하지 못했습니다"
    )
}
