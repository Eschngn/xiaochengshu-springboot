package com.chengliuxiang.xiaochengshu.count.biz.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.count.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.count.biz.domain.mapper.UserCountDOMapper;
import com.chengliuxiang.xiaochengshu.count.biz.enums.FollowUnfollowTypeEnum;
import com.chengliuxiang.xiaochengshu.count.biz.model.dto.CountFollowUnfollowMqDTO;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_COUNT_FOLLOWING_2_DB,
        topic = MQConstants.TOPIC_COUNT_FOLLOWING_2_DB)
public class CountFollowing2DBConsumer implements RocketMQListener<String> {
    @Resource
    private UserCountDOMapper userCountDOMapper;

    private RateLimiter rateLimiter = RateLimiter.create(5000);


    @Override
    public void onMessage(String body) {
        rateLimiter.acquire();
        if (StringUtils.isBlank(body)) return;
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        Integer type = countFollowUnfollowMqDTO.getType();
        Long userId = countFollowUnfollowMqDTO.getUserId();
        int count = Objects.equals(type, FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;
        userCountDOMapper.insertOrUpdateFollowingTotalByUserId(count, userId);
    }
}
