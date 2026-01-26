package com.chengliuxiang.xiaochengshu.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.count.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.count.biz.domain.mapper.NoteCountDOMapper;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB,
        topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB)
@Slf4j
public class CountNoteCollect2DBConsumer implements RocketMQListener<String> {
    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    private final RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String body) {
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 笔记收藏数入库】, {}...", body);
        Map<Long, Integer> countMap = Maps.newHashMap();
        try {
            countMap = JsonUtils.parseMap(body, Long.class, Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }
        if (CollUtil.isNotEmpty(countMap)) {
            countMap.forEach((k, v) -> noteCountDOMapper.insertOrUpdateCollectTotalByNoteId(v, k));
        }
    }
}
