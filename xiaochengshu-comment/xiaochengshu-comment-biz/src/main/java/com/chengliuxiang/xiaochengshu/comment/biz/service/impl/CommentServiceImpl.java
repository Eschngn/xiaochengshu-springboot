package com.chengliuxiang.xiaochengshu.comment.biz.service.impl;

import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.comment.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.comment.biz.model.dto.PublishCommentMqDTO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.PublishCommentReqVO;
import com.chengliuxiang.xiaochengshu.comment.biz.retry.SendMqRetryHelper;
import com.chengliuxiang.xiaochengshu.comment.biz.service.CommentService;
import com.google.common.base.Preconditions;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Resource
    private SendMqRetryHelper sendMqRetryHelper;

    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        String content = publishCommentReqVO.getContent(); // 评论正文
        // TODO：后续需要支持上传多张图片
        String imageUrl = publishCommentReqVO.getImageUrl(); // 评论图片
        Preconditions.checkArgument(StringUtils.isNotEmpty(content) || StringUtils.isNotEmpty(imageUrl),
                "评论正文和图片不能同时为空");
        // 发布者 ID
        Long creatorId = LoginUserContextHolder.getUserId();
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .noteId(publishCommentReqVO.getNoteId())
                .content(content)
                .imageUrl(imageUrl)
                .replyCommentId(publishCommentReqVO.getReplyCommentId())
                .createTime(LocalDateTime.now())
                .creatorId(creatorId)
                .build();
        // 发送 MQ（包含重试机制）
        sendMqRetryHelper.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT, JsonUtils.toJsonString(publishCommentMqDTO));
        return Response.success();
    }
}
