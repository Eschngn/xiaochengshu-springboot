package com.chengliuxiang.xiaochengshu.count.biz.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.count.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.count.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.count.biz.enums.LikeUnlikeNoteTypeEnum;
import com.chengliuxiang.xiaochengshu.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE,
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE)
@Slf4j
public class CountNoteLikeConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    private final BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String body) {
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        List<CountLikeUnlikeNoteMqDTO> countLikeUnlikeNoteMqDTOS
                = bodys.stream().map(body -> JsonUtils.parseObject(body, CountLikeUnlikeNoteMqDTO.class)).toList();
        // 按笔记 ID 进行分组
        Map<Long, List<CountLikeUnlikeNoteMqDTO>> groupMap = countLikeUnlikeNoteMqDTOS.stream()
                .collect(Collectors.groupingBy(CountLikeUnlikeNoteMqDTO::getNoteId));
        Map<Long, Integer> countMap = Maps.newHashMap();
        for (Map.Entry<Long, List<CountLikeUnlikeNoteMqDTO>> entry : groupMap.entrySet()) {
            List<CountLikeUnlikeNoteMqDTO> list = entry.getValue();
            int finalCount = 0;
            for (CountLikeUnlikeNoteMqDTO countLikeUnlikeNoteMqDTO : list) {
                Integer type = countLikeUnlikeNoteMqDTO.getType();
                LikeUnlikeNoteTypeEnum likeUnlikeNoteTypeEnum = LikeUnlikeNoteTypeEnum.valueOf(type);
                if (Objects.isNull(likeUnlikeNoteTypeEnum)) continue;
                switch (likeUnlikeNoteTypeEnum) {
                    case LIKE -> finalCount += 1;
                    case UNLIKE -> finalCount -= 1;
                }
            }
            countMap.put(entry.getKey(), finalCount);
        }
        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));

        // 更新 Redis
        countMap.forEach((k, v) -> {
            String redisKey = RedisKeyConstants.buildCountNoteKey(k);
            Boolean isExisted = redisTemplate.hasKey(redisKey);
            if (isExisted) {
                redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, v);
            }
        });

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countMap))
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：笔记点赞数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：笔记点赞数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
