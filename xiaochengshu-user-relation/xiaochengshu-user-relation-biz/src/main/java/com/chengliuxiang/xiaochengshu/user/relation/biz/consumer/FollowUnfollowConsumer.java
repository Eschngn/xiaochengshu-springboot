package com.chengliuxiang.xiaochengshu.user.relation.biz.consumer;

import com.chengliuxiang.framework.common.util.DateUtils;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.user.relation.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.user.relation.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.dataobject.FansDO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.dataobject.FollowingDO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.mapper.FansDOMapper;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.chengliuxiang.xiaochengshu.user.relation.biz.enums.FollowUnfollowTypeEnum;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.dto.CountFollowUnfollowMqDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.dto.UnfollowUserMqDTO;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group" + MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW,
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW,
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
@Slf4j
public class FollowUnfollowConsumer implements RocketMQListener<Message> {
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private FansDOMapper fansDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private RateLimiter rateLimiter;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();
        log.info("==> FollowUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        if (Objects.equals(tags, MQConstants.TAG_FOLLOW)) { // 关注
            handleFollowTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UNFOLLOW)) { // 取关
            handleUnfollowTagMessage(bodyJsonStr);
        }
    }

    private void handleFollowTagMessage(String bodyJsonStr) {
        FollowUserMqDTO followUserMqDTO = JsonUtils.parseObject(bodyJsonStr, FollowUserMqDTO.class);
        if (Objects.isNull(followUserMqDTO)) return;
        // 幂等性：通过联合唯一索引保证
        Long userId = followUserMqDTO.getUserId();
        Long followUserId = followUserMqDTO.getFollowUserId();
        LocalDateTime createTime = followUserMqDTO.getCreateTime();
        // 编程式提交事务
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                int count = followingDOMapper.insert(FollowingDO.builder()
                        .userId(userId)
                        .followingUserId(followUserId)
                        .createTime(createTime).build());
                if (count > 0) {
                    fansDOMapper.insert(FansDO.builder()
                            .userId(followUserId)
                            .fansUserId(userId)
                            .createTime(createTime).build());
                }
                return true;
            } catch (Exception e) {
                status.setRollbackOnly(); // 标记事务为回滚
                log.error("", e);
            }
            return false;
        }));
        // 更新 Redis 中被关注用户的 ZSet 粉丝列表
        if (isSuccess) {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_update_fans.lua")));
            script.setResultType(Long.class);
            String fansRedisKey = RedisKeyConstants.buildUserFansKey(followUserId);
            long timestamp = DateUtils.localDateTime2Timestamp(createTime);
            redisTemplate.execute(script, Collections.singletonList(fansRedisKey), userId, timestamp);

            // 发送 MQ 进行计数
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(userId)
                    .targetUserId(followUserId)
                    .type(FollowUnfollowTypeEnum.FOLLOW.getCode())
                    .build();
            sendMQ(countFollowUnfollowMqDTO);
        }
    }

    private void handleUnfollowTagMessage(String bodyJsonStr) {
        UnfollowUserMqDTO unfollowUserMqDTO = JsonUtils.parseObject(bodyJsonStr, UnfollowUserMqDTO.class);
        if (Objects.isNull(unfollowUserMqDTO)) return;
        Long userId = unfollowUserMqDTO.getUserId();
        Long unfollowUserId = unfollowUserMqDTO.getUnfollowUserId();
        LocalDateTime createTime = unfollowUserMqDTO.getCreateTime();

        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                int count = followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);
                if (count > 0) {
                    fansDOMapper.deleteByUserIdAndFansUserId(unfollowUserId, userId);
                }
                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("", e);
            }
            return false;
        }));
        if (isSuccess) {
            String fansRedisKey = RedisKeyConstants.buildUserFansKey(unfollowUserId);
            redisTemplate.opsForZSet().remove(fansRedisKey, userId);

            // 发送 MQ 进行计数
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(userId)
                    .targetUserId(unfollowUserId)
                    .type(FollowUnfollowTypeEnum.UNFOLLOW.getCode())
                    .build();
            sendMQ(countFollowUnfollowMqDTO);
        }
    }

    private void sendMQ(CountFollowUnfollowMqDTO countFollowUnfollowMqDTO) {
        org.springframework.messaging.Message<CountFollowUnfollowMqDTO> message = MessageBuilder.withPayload(countFollowUnfollowMqDTO).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FOLLOWING, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：关注数】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：关注数】MQ 发送异常: ", throwable);
            }
        });
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：粉丝数】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：粉丝数】MQ 发送异常: ", throwable);
            }
        });
    }
}
