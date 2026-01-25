package com.chengliuxiang.xiaochengshu.note.biz.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.note.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.note.biz.domain.dataobject.NoteCollectionDO;
import com.chengliuxiang.xiaochengshu.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.chengliuxiang.xiaochengshu.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT,
        topic = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT,
        consumeMode = ConsumeMode.ORDERLY)
@Slf4j
public class CollectUnCollectNoteConsumer implements RocketMQListener<Message> {
    private final RateLimiter rateLimiter=RateLimiter.create(5000);
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;

    @Override
    public void onMessage(Message message) {
        rateLimiter.acquire();
        String bodyJsonStr=new String(message.getBody());
        String tags = message.getTags();
        log.info("==> CollectUnCollectNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        if (Objects.equals(tags, MQConstants.TAG_COLLECT)) { // 收藏笔记
            handleCollectNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UN_COLLECT)) { // 取消收藏笔记
            handleUnCollectNoteTagMessage(bodyJsonStr);
        }
    }

    /**
     * 笔记收藏
     * @param bodyJsonStr
     */
    private void handleCollectNoteTagMessage(String bodyJsonStr) {
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);
        Long userId = collectUnCollectNoteMqDTO.getUserId();
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        Integer type = collectUnCollectNoteMqDTO.getType();
        LocalDateTime createTime = collectUnCollectNoteMqDTO.getCreateTime();
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();
        int count = noteCollectionDOMapper.insertOrUpdate(noteCollectionDO);

    }

    /**
     * 笔记取消收藏
     * @param bodyJsonStr
     */
    private void handleUnCollectNoteTagMessage(String bodyJsonStr) {
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);
        if (Objects.isNull(collectUnCollectNoteMqDTO)) return;
        Long userId = collectUnCollectNoteMqDTO.getUserId();
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        Integer type = collectUnCollectNoteMqDTO.getType();
        LocalDateTime createTime = collectUnCollectNoteMqDTO.getCreateTime();
        NoteCollectionDO collectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .status(type)
                .createTime(createTime)
                .build();
        int count = noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(collectionDO);
    }
}
