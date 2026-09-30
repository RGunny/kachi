package me.rgunny.kachi.experiments.qdranttransport;

import java.util.List;

/**
 * Qdrant에 같은 작업을 보내는 전송 방식.
 *
 * REST와 gRPC 구현이 이 계약을 같이 지키므로 벤치마크는 어느 쪽인지 모르고 부하만 준다.
 */
public interface Transport extends AutoCloseable {

    String name();

    /**
     * 컬렉션을 지우고 다시 만든다. payload index를 붙일지는 검색 조건에 따라 갈린다.
     */
    void recreateCollection(boolean withPayloadIndex);

    OpResult upsert(List<Point> batch);

    OpResult search(float[] vector, long collectedAfter, int topK);
}
