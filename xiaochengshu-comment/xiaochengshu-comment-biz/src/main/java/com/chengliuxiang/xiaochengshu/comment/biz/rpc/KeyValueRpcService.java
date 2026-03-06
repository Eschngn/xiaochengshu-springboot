package com.chengliuxiang.xiaochengshu.comment.biz.rpc;

import com.chengliuxiang.framework.common.constant.DateConstants;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.comment.biz.model.bo.CommentBO;
import com.chengliuxiang.xiaochengshu.kv.api.KeyValueFeignApi;
import com.chengliuxiang.xiaochengshu.kv.dto.req.BatchAddCommentContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.req.CommentContentReqDTO;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KeyValueRpcService {

    @Resource
    private KeyValueFeignApi keyValueFeignApi;

    /**
     * 评论存储评论内容
     *
     * @param commentBOS
     * @return
     */
    public boolean batchSaveCommentContent(List<CommentBO> commentBOS) {
        List<CommentContentReqDTO> comments = Lists.newArrayList();

        // BO 转 DTO
        commentBOS.forEach(commentBO -> {
            CommentContentReqDTO commentContentReqDTO = CommentContentReqDTO.builder()
                    .noteId(commentBO.getNoteId())
                    .content(commentBO.getContent())
                    .contentId(commentBO.getContentUuid())
                    .yearMonth(commentBO.getCreateTime().format(DateConstants.DATE_FORMAT_Y_M))
                    .build();
            comments.add(commentContentReqDTO);
        });

        BatchAddCommentContentReqDTO batchAddCommentContentReqDTO =
                BatchAddCommentContentReqDTO.builder().comments(comments).build();

        Response<?> response = keyValueFeignApi.batchAddCommentContent(batchAddCommentContentReqDTO);
        if (!response.isSuccess()) {
            throw new RuntimeException("批量保存评论内容失败");
        }
        return true;
    }
}
