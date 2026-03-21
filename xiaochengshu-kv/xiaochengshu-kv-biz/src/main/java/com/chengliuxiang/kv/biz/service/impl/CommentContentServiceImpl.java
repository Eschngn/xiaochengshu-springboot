package com.chengliuxiang.kv.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.kv.biz.domain.dataobject.CommentContentDO;
import com.chengliuxiang.kv.biz.domain.dataobject.CommentContentPrimaryKey;
import com.chengliuxiang.kv.biz.domain.repository.CommentContentRepository;
import com.chengliuxiang.kv.biz.service.CommentContentService;
import com.chengliuxiang.xiaochengshu.kv.dto.req.BatchAddCommentContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.req.BatchFindCommentContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.req.CommentContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.req.FindCommentContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.rsp.FindCommentContentRspDTO;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CommentContentServiceImpl implements CommentContentService {

    @Resource
    private CassandraTemplate cassandraTemplate;
    @Resource
    private CommentContentRepository commentContentRepository;

    /**
     * 批量添加评论内容
     *
     * @param batchAddCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        List<CommentContentReqDTO> comments = batchAddCommentContentReqDTO.getComments();

        // DTO 转 DO
        List<CommentContentDO> contentDOS = comments.stream()
                .map(commentContentReqDTO -> {
                    // 构建主键类
                    CommentContentPrimaryKey commentContentPrimaryKey = CommentContentPrimaryKey.builder()
                            .noteId(commentContentReqDTO.getNoteId())
                            .yearMonth(commentContentReqDTO.getYearMonth())
                            .contentId(UUID.fromString(commentContentReqDTO.getContentId()))
                            .build();

                    return CommentContentDO.builder()
                            .primaryKey(commentContentPrimaryKey)
                            .content(commentContentReqDTO.getContent())
                            .build();
                }).toList();

        // 批量插入
        cassandraTemplate.batchOps()
                .insert(contentDOS).execute();

        return Response.success();
    }


    /**
     * 批量查询评论内容
     *
     * @param batchFindCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> batchFindCommentContent(BatchFindCommentContentReqDTO batchFindCommentContentReqDTO) {
        Long noteId = batchFindCommentContentReqDTO.getNoteId();
        List<FindCommentContentReqDTO> commentContentKeys = batchFindCommentContentReqDTO.getCommentContentKeys();

        List<String> yearMonths = commentContentKeys.stream().map(FindCommentContentReqDTO::getYearMonth).distinct().toList();
        List<UUID> contentIds = commentContentKeys.stream()
                .map(commentContentKey -> UUID.fromString(commentContentKey.getContentId()))
                .distinct()
                .toList();

        List<CommentContentDO> commentContentDOS = commentContentRepository
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(noteId, yearMonths, contentIds);

        // DO 转 DTO
        List<FindCommentContentRspDTO> findCommentContentRspDTOS = Lists.newArrayList();
        if (CollUtil.isNotEmpty(commentContentDOS)) {
            findCommentContentRspDTOS = commentContentDOS.stream()
                    .map(commentContentDO -> FindCommentContentRspDTO.builder()
                            .contentId(String.valueOf(commentContentDO.getPrimaryKey().getContentId()))
                            .content(commentContentDO.getContent())
                            .build())
                    .toList();
        }
        return Response.success(findCommentContentRspDTOS);
    }
}
