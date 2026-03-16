package com.chengliuxiang.xiaochengshu.count.biz.consumer;


import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.count.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.count.biz.domain.mapper.CommentDOMapper;
import com.chengliuxiang.xiaochengshu.count.biz.enums.CommentLevelEnum;
import com.chengliuxiang.xiaochengshu.count.biz.model.dto.CountPublishCommentMqDTO;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_child_comment_total_" + MQConstants.TOPIC_COUNT_NOTE_COMMENT,
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT)
@Slf4j
public class CountNoteChildCommentConsumer implements RocketMQListener<String> {

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次（1s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();
    @Resource
    private CommentDOMapper commentDOMapper;

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记二级评论数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记二级评论数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        List<CountPublishCommentMqDTO> countPublishCommentMqDTOList = Lists.newArrayList();
        bodys.forEach(body -> {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(body, CountPublishCommentMqDTO.class);
                countPublishCommentMqDTOList.addAll(list);
            } catch (Exception e) {
                log.error("", e);
            }
        });
        // 过滤出二级评论 并按 parent_id 分组
        Map<Long, List<CountPublishCommentMqDTO>> groupMap = countPublishCommentMqDTOList.stream()
                .filter(countPublishCommentMqDTO -> Objects.equals(countPublishCommentMqDTO.getLevel(), CommentLevelEnum.TWO.getCode()))
                .collect(Collectors.groupingBy(CountPublishCommentMqDTO::getParentId));
        if (Objects.isNull(groupMap)) return;

        for (Map.Entry<Long, List<CountPublishCommentMqDTO>> entry : groupMap.entrySet()) {
            Long parentId = entry.getKey();
            int count = entry.getValue().size();
            commentDOMapper.updateChildCommentTotal(parentId, count);
        }


        // TODO:
    }
}
