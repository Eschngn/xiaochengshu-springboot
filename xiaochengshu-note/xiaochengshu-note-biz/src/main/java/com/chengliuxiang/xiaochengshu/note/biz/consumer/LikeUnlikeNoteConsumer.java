package com.chengliuxiang.xiaochengshu.note.biz.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.note.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.note.biz.domain.dataobject.NoteLikeDO;
import com.chengliuxiang.xiaochengshu.note.biz.domain.mapper.NoteLikeDOMapper;
import com.chengliuxiang.xiaochengshu.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_LIKE_OR_UNLIKE,
        topic = MQConstants.TOPIC_LIKE_OR_UNLIKE,
        consumeMode = ConsumeMode.ORDERLY)
@Slf4j
public class LikeUnlikeNoteConsumer implements RocketMQListener<Message> {
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Message message) {
        rateLimiter.acquire();
        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();
        log.info("==> LikeUnlikeNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        if(Objects.equals(tags, MQConstants.TAG_LIKE)){ // 点赞笔记
            handleLikeNoteTagMessage(bodyJsonStr);
        }else if(Objects.equals(tags, MQConstants.TAG_UNLIKE)){ // 取消点赞笔记
            handleUnlikeNoteTagMessage(bodyJsonStr);
        }

    }

    /**
     * 笔记点赞
     * @param bodyJsonStr
     */
    private void handleLikeNoteTagMessage(String bodyJsonStr) {
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);
        if(Objects.isNull(likeUnlikeNoteMqDTO)) return;

        Long userId = likeUnlikeNoteMqDTO.getUserId();
        Long noteId = likeUnlikeNoteMqDTO.getNoteId();
        Integer type = likeUnlikeNoteMqDTO.getType();
        LocalDateTime createTime = likeUnlikeNoteMqDTO.getCreateTime();
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type).build();
        int count = noteLikeDOMapper.insertOrUpdate(noteLikeDO);

        if(count==0) return;
        // 发送计数 MQ
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记点赞】MQ 发送异常: ", throwable);
            }
        });
    }

    /**
     * 笔记取消点赞
     * @param bodyJsonStr
     */
    private void handleUnlikeNoteTagMessage(String bodyJsonStr) {
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);
        if(Objects.isNull(likeUnlikeNoteMqDTO)) return;
        Long userId = likeUnlikeNoteMqDTO.getUserId();
        Long noteId = likeUnlikeNoteMqDTO.getNoteId();
        Integer type = likeUnlikeNoteMqDTO.getType();
        LocalDateTime createTime = likeUnlikeNoteMqDTO.getCreateTime();
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type).build();
        int count = noteLikeDOMapper.update2UnlikeByUserIdAndNoteId(noteLikeDO);
        if(count==0) return;
        // 发送计数 MQ
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数: 笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数: 笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });
    }

}
