package com.chengliuxiang.xiaochengshu.count.biz.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.count.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.count.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.count.biz.domain.mapper.UserCountDOMapper;
import com.chengliuxiang.xiaochengshu.count.biz.model.dto.NoteOperateMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_NOTE_OPERATE,
        topic = MQConstants.TOPIC_NOTE_OPERATE)
@Slf4j
public class CountNotePublishConsumer implements RocketMQListener<Message> {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserCountDOMapper userCountDOMapper;


    @Override
    public void onMessage(Message message) {
        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();
        log.info("==> CountNotePublishConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        if (Objects.equals(tags, MQConstants.TAG_NOTE_PUBLISH)) {
            handleTagMessage(bodyJsonStr, 1);
        } else if (Objects.equals(tags, MQConstants.TAG_NOTE_DELETE)) {
            handleTagMessage(bodyJsonStr, -1);
        }

    }

    private void handleTagMessage(String bodyJsonStr, Integer count) {
        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(bodyJsonStr, NoteOperateMqDTO.class);
        if (Objects.isNull(noteOperateMqDTO)) return;
        Long creatorId = noteOperateMqDTO.getCreatorId();
        String countUserRedisKey = RedisKeyConstants.buildCountUserKey(creatorId);
        boolean isCountUserExisted = redisTemplate.hasKey(countUserRedisKey);
        if (isCountUserExisted) {
            redisTemplate.opsForHash().increment(countUserRedisKey, RedisKeyConstants.FIELD_NOTE_TOTAL, count);
        }
        userCountDOMapper.insertOrUpdateNoteTotalByUserId(count, creatorId);
    }
}
