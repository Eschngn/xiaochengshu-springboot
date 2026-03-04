package com.chengliuxiang.xiaochengshu.comment.biz.service.impl;

import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.comment.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.comment.biz.model.dto.PublishCommentMqDTO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.PublishCommentReqVO;
import com.chengliuxiang.xiaochengshu.comment.biz.service.CommentService;
import com.google.common.base.Preconditions;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

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
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(publishCommentMqDTO)).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论发布】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论发布】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }
}
