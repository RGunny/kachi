package me.rgunny.kachi.story.adapter.inbound.index

/**
 * 색인 재구축 시작 요청의 결과.
 *
 * 재구축 본체는 요청 밖에서 돌아, 시작됐다는 것과 완료됐다는 것이 다르다.
 * 다른 실행이 lock을 쥐고 있어 건너뛴 것은 정상이고, lock을 확인할 수 없어 시작하지 못한 것은 장애라 둘을 나눈다.
 */
sealed interface IndexRebuildExecution
