package com.chengliuxiang.xiaochengshu.search.biz.service.impl;

import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.xiaochengshu.search.biz.index.UserIndex;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserReqVO;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserRspVO;
import com.chengliuxiang.xiaochengshu.search.biz.service.UserSearchService;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class UserSearchServiceImpl implements UserSearchService {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    public PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO) {
        String keyword = searchUserReqVO.getKeyword();
        Integer pageNo = searchUserReqVO.getPageNo();
        SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 构建 multi_match 查询，查询 nickname 和 xiaochengshu_id 字段
        searchSourceBuilder.query(QueryBuilders.multiMatchQuery(
                keyword, UserIndex.FIELD_USER_NICKNAME, UserIndex.FIELD_USER_XIAOCHENGSHU_ID));
        // 排序，按 fans_total 降序
        SortBuilder<?> sortBuilder = new FieldSortBuilder(UserIndex.FIELD_USER_FANS_TOTAL)
                .order(SortOrder.DESC);
        searchSourceBuilder.sort(sortBuilder);

        int pageSize = 10;
        int from = (pageNo - 1) * pageSize; // 偏移量

        searchSourceBuilder.from(from);
        searchSourceBuilder.size(pageSize);
        searchRequest.source(searchSourceBuilder); // 将构建的查询条件设置到 SearchRequest 中
        List<SearchUserRspVO> searchUserRspVOS = null;
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            total = Objects.requireNonNull(searchResponse.getHits().getTotalHits()).value;
            log.info("==> 命中文档总数，hit: {}", total);
            searchUserRspVOS = Lists.newArrayList();
            SearchHits hits = searchResponse.getHits();
            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());
                Map<String, Object> sourceAsMap = hit.getSourceAsMap(); // 获取文档的所有字段（以 Map 的形式返回）
                Long userId = ((Number) sourceAsMap.get(UserIndex.FIELD_USER_ID)).longValue();
                String nickname = (String) sourceAsMap.get(UserIndex.FIELD_USER_NICKNAME);
                String avatar = (String) sourceAsMap.get(UserIndex.FIELD_USER_AVATAR);
                String xiaochengshuId = (String) sourceAsMap.get(UserIndex.FIELD_USER_XIAOCHENGSHU_ID);
                Integer noteTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_NOTE_TOTAL);
                Integer fansTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_FANS_TOTAL);

                SearchUserRspVO searchUserRspVO = SearchUserRspVO.builder()
                        .userId(userId)
                        .nickname(nickname)
                        .avatar(avatar)
                        .xiaochengshuId(xiaochengshuId)
                        .noteTotal(noteTotal)
                        .fansTotal(fansTotal).build();
                searchUserRspVOS.add(searchUserRspVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 ElasticSearch 异常：", e);
        }
        return PageResponse.success(searchUserRspVOS, pageNo, pageSize);
    }
}
