package me.rgunny.kachi.experiments.qdranttransport;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Datatype;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Range;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsOperationResponse;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.QueryResponse;
import io.qdrant.client.grpc.Points.UpsertPoints;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.ConditionFactory.range;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.include;

/**
 * Qdrant gRPC 전송. 공식 Java 클라이언트 `io.qdrant:client`를 쓴다.
 *
 * 측정하는 두 작업은 stub을 직접 불러 응답 메시지 전체를 받는다. 감싼 메서드는 결과만 돌려줘서 응답 크기를 못 잰다.
 */
public final class GrpcTransport implements Transport {

    private final Settings settings;
    private final QdrantGrpcClient grpcClient;
    private final QdrantClient client;

    public GrpcTransport(Settings settings) {
        this.settings = settings;
        this.grpcClient = QdrantGrpcClient.newBuilder(settings.host(), settings.grpcPort(), false)
                .withTimeout(Duration.ofSeconds(30))
                .build();
        this.client = new QdrantClient(grpcClient);
    }

    @Override
    public String name() {
        return "grpc";
    }

    @Override
    public void recreateCollection(boolean withPayloadIndex) {
        if (Boolean.TRUE.equals(await(client.collectionExistsAsync(settings.collectionName())))) {
            await(client.deleteCollectionAsync(settings.collectionName()));
        }
        await(client.createCollectionAsync(settings.collectionName(), VectorParams.newBuilder()
                .setSize(settings.dimension())
                .setDistance(Distance.Cosine)
                .setDatatype(Datatype.Float32)
                .build()));

        if (!withPayloadIndex) {
            return;
        }
        createIndex("storyId", PayloadSchemaType.Keyword);
        createIndex("collectedAt", PayloadSchemaType.Integer);
        createIndex("language", PayloadSchemaType.Keyword);
    }

    @Override
    public OpResult upsert(List<Point> batch) {
        List<PointStruct> points = new ArrayList<>(batch.size());
        for (Point point : batch) {
            Map<String, Value> payload = new HashMap<>();
            payload.put("storyId", value(point.storyId()));
            payload.put("collectedAt", value(point.collectedAt()));
            payload.put("language", value(point.language()));
            points.add(PointStruct.newBuilder()
                    .setId(id(point.id()))
                    .setVectors(vectors(point.vector()))
                    .putAllPayload(payload)
                    .build());
        }
        UpsertPoints request = UpsertPoints.newBuilder()
                .setCollectionName(settings.collectionName())
                .setWait(true)
                .addAllPoints(points)
                .build();
        PointsOperationResponse response = await(grpcClient.points().upsert(request));
        return new OpResult(batch.size(), request.getSerializedSize(), response.getSerializedSize());
    }

    @Override
    public OpResult search(float[] vector, long collectedAfter, int topK) {
        Filter filter = Filter.newBuilder()
                .addMust(range("collectedAt", Range.newBuilder().setGte(collectedAfter).build()))
                .build();
        QueryPoints request = QueryPoints.newBuilder()
                .setCollectionName(settings.collectionName())
                .setQuery(nearest(vector))
                .setFilter(filter)
                .setLimit(topK)
                .setWithPayload(include(List.of("storyId")))
                .build();
        QueryResponse response = await(grpcClient.points().query(request));
        return new OpResult(response.getResultCount(), request.getSerializedSize(), response.getSerializedSize());
    }

    @Override
    public void close() throws Exception {
        client.close();
        grpcClient.close();
    }

    private void createIndex(String field, PayloadSchemaType schema) {
        await(client.createPayloadIndexAsync(settings.collectionName(), field, schema, null, true, null, null));
    }

    private <T> T await(com.google.common.util.concurrent.ListenableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("gRPC 호출이 중단됐다", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("gRPC 호출이 실패했다", e.getCause());
        }
    }
}
