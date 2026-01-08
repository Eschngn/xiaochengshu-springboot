package com.chengliuxiang.xiaochengshu.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.count.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.count.biz.domain.mapper.UserCountDOMapper;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_COUNT_FANS_2_DB,
        topic = MQConstants.TOPIC_COUNT_FANS_2_DB)
@Slf4j
public class CountFans2DBConsumer implements RocketMQListener<String> {
    // 每秒创建 5000 个令牌
    private final RateLimiter rateLimiter = RateLimiter.create(5000);
    @Resource
    private UserCountDOMapper userCountDOMapper;

    @Override
    public void onMessage(String body) {
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 粉丝数入库】, {}...", body);
        Map<Long, Integer> countMap = null;
        try {
            countMap = JsonUtils.parseMap(body, Long.class, Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }
        if (CollUtil.isNotEmpty(countMap)) {
            countMap.forEach((k, v) -> userCountDOMapper.insertOrUpdateFansTotalByUserId(v, k));
        }
    }
}
