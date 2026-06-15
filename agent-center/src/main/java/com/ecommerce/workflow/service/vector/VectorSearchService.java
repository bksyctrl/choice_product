package com.ecommerce.workflow.service.vector;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import com.ecommerce.workflow.service.config.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private static final String COLLECTION_NAME = "memory_vectors";
    private static final String VECTOR_FIELD = "embedding";
    private static final String ID_FIELD = "memory_id";
    private static final String CONTENT_FIELD = "content";

    private final SysConfigService sysConfigService;

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;
    
    @Value("${milvus.enabled:true}")
    private boolean milvusEnabled;

    private MilvusServiceClient milvusClient;

    public VectorSearchService(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @PostConstruct
    public void init() {
        if (!milvusEnabled) {
            log.info("Milvus 服务已禁用，VectorSearchService未初始化");
            return;
        }
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(milvusHost)
                .withPort(milvusPort)
                .build();

            milvusClient = new MilvusServiceClient(connectParam);
            
            log.info("Milvus客户端连接成功: {}:{}", milvusHost, milvusPort);
            
            initCollection();
        } catch (Exception e) {
            log.error("Milvus客户端连接失败", e);
        }
    }

    private void initCollection() {
        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build());

            if (hasCollection.getData() == Boolean.FALSE) {
                FieldType idField = FieldType.newBuilder()
                    .withName(ID_FIELD)
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();

                FieldType contentField = FieldType.newBuilder()
                    .withName(CONTENT_FIELD)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .build();

                FieldType vectorField = FieldType.newBuilder()
                    .withName(VECTOR_FIELD)
                    .withDataType(DataType.FloatVector)
                    .withDimension(sysConfigService.getIntConfig("vector_dimension", 1536))
                    .build();

                CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withDescription("Memory vector storage")
                    .withShardsNum(2)
                    .addFieldType(idField)
                    .addFieldType(contentField)
                    .addFieldType(vectorField)
                    .build();

                R<RpcStatus> createResult = milvusClient.createCollection(createParam);
                
                if (createResult.getStatus() == R.Status.Success.getCode()) {
                    log.info("Milvus集合创建成功: {}", COLLECTION_NAME);
                    
                    createIndex();
                }
            } else {
                log.info("Milvus集合已存在: {}", COLLECTION_NAME);
            }
        } catch (Exception e) {
            log.error("初始化Milvus集合失败", e);
        }
    }

    private void createIndex() {
        try {
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName(VECTOR_FIELD)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":1024}")
                .withSyncMode(Boolean.TRUE)
                .build();

            R<RpcStatus> createIndexResult = milvusClient.createIndex(indexParam);
            
            if (createIndexResult.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus索引创建成功");
            }
        } catch (Exception e) {
            log.error("Milvus索引创建失败", e);
        }
    }

    public boolean vectorizeMemory(Long memoryId, String content, List<Float> vector) {
        try {
            List<InsertParam.Field> fields = new ArrayList<>();
            
            List<Long> idArray = new ArrayList<>();
            idArray.add(memoryId);
            fields.add(new InsertParam.Field(ID_FIELD, idArray));

            List<String> contentArray = new ArrayList<>();
            contentArray.add(content);
            fields.add(new InsertParam.Field(CONTENT_FIELD, contentArray));

            List<List<Float>> vectorArray = new ArrayList<>();
            vectorArray.add(vector);
            fields.add(new InsertParam.Field(VECTOR_FIELD, vectorArray));

            InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

            R<MutationResult> insertResult = milvusClient.insert(insertParam);
            
            if (insertResult.getStatus() == R.Status.Success.getCode()) {
                log.info("记忆向量存储成功: memoryId={}", memoryId);
                return true;
            }
        } catch (Exception e) {
            log.error("记忆向量存储失败: memoryId={}", memoryId, e);
        }
        
        return false;
    }

    public List<MemorySearchResult> searchByVector(List<Float> queryVector, int topK) {
        try {
            List<String> searchOutputFields = Arrays.asList(ID_FIELD, CONTENT_FIELD);

            SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.L2)
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryVector))
                .withVectorFieldName(VECTOR_FIELD)
                .withOutFields(searchOutputFields)
                .build();

            R<SearchResults> searchResult = milvusClient.search(searchParam);
            
            if (searchResult.getStatus() == R.Status.Success.getCode()) {
                SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResult.getData().getResults());
                
                List<MemorySearchResult> results = new ArrayList<>();
                List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
                for (int i = 0; i < idScores.size(); i++) {
                    SearchResultsWrapper.IDScore score = idScores.get(i);
                    MemorySearchResult result = new MemorySearchResult();
                    result.setMemoryId((Long) score.getLongID());
                    result.setScore(score.getScore());
                    
                    @SuppressWarnings("deprecation")
                    Map<String, Object> fieldValues = wrapper.getRowRecords().get(i).getFieldValues();
                    result.setContent((String) fieldValues.get(CONTENT_FIELD));
                    
                    results.add(result);
                }
                
                log.info("相似记忆搜索完成，返回 {} 条结果", results.size());
                return results;
            }
        } catch (Exception e) {
            log.error("相似记忆搜索失败", e);
        }
        
        return Collections.emptyList();
    }

    public boolean deleteMemoryVector(Long memoryId) {
        try {
            String expression = ID_FIELD + " == " + memoryId;
            
            R<MutationResult> deleteResult = milvusClient.delete(
                io.milvus.param.dml.DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withExpr(expression)
                    .build()
            );
            
            if (deleteResult.getStatus() == R.Status.Success.getCode()) {
                log.info("删除记忆向量成功: memoryId={}", memoryId);
                return true;
            }
        } catch (Exception e) {
            log.error("删除记忆向量失败: memoryId={}", memoryId, e);
        }
        
        return false;
    }

    public static class MemorySearchResult {
        private Long memoryId;
        private String content;
        private float score;

        public Long getMemoryId() {
            return memoryId;
        }

        public void setMemoryId(Long memoryId) {
            this.memoryId = memoryId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }
    }
}
