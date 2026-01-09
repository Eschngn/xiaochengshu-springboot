package com.chengliuxiang.xiaochengshu.count.biz.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.count.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.count.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.count.biz.enums.FollowUnfollowTypeEnum;
import com.chengliuxiang.xiaochengshu.count.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_COUNT_FOLLOWING,
        topic = MQConstants.TOPIC_COUNT_FOLLOWING)
@Slf4j
public class CountFollowingConsumer implements RocketMQListener<String> {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 关注数】, {}...", body);
        if (StringUtils.isBlank(body)) return;
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        Integer type = countFollowUnfollowMqDTO.getType();
        Long userId = countFollowUnfollowMqDTO.getUserId();
        String redisKey = RedisKeyConstants.buildCountUserKey(userId);
        Boolean isExisted = redisTemplate.hasKey(redisKey);
        if (isExisted) {
            long count = Objects.equals(type, FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;
            redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIElD_FOLLOWING_TOTAL, count);
        }
        Message<String> message = MessageBuilder.withPayload(body)
                .build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FOLLOWING_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：关注数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：关注数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
