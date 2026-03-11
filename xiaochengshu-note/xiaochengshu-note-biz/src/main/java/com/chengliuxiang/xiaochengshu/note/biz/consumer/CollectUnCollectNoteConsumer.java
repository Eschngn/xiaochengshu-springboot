package com.chengliuxiang.xiaochengshu.note.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.note.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.note.biz.domain.dataobject.NoteCollectionDO;
import com.chengliuxiang.xiaochengshu.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.chengliuxiang.xiaochengshu.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CollectUnCollectNoteConsumer {

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;

    private DefaultMQPushConsumer consumer;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Bean(name = "CollectUnCollectNoteConsumer")
    public DefaultMQPushConsumer consumer() throws MQClientException {
        // Group 组
        String group = "xiaochengshu_group1_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT;

        // 创建一个新的 DefaultMQPushConsumer 实例，并指定消费者的消费组名
        consumer = new DefaultMQPushConsumer(group);

        // 设置 RocketMQ 的 NameServer 地址
        consumer.setNamesrvAddr(namesrvAddr);

        // 订阅指定的主题，并设置主题的订阅规则（"*" 表示订阅所有标签的消息）
        consumer.subscribe(MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, "*");

        // 设置消费者消费消息的起始位置，如果队列中没有消息，则从最新的消息开始消费。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 设置消息消费模式，这里使用集群模式 (CLUSTERING)
        consumer.setMessageModel(MessageModel.CLUSTERING);

        // 最大重试次数, 以防消息重试过多次仍然没有成功，避免消息卡在消费队列中。
        consumer.setMaxReconsumeTimes(3);
        // 设置每批次消费的最大消息数量，这里设置为 30，表示每次拉取时最多消费 30 条消息。
        consumer.setConsumeMessageBatchMaxSize(30);
        // 设置拉取间隔, 单位毫秒
        consumer.setPullInterval(1000);

        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerOrderly) (msgs, context) -> {
            log.info("==> 【笔记收藏、取消收藏】本批次消息大小: {}", msgs.size());
            try {
                rateLimiter.acquire();
                List<CollectUnCollectNoteMqDTO> collectUnCollectNoteMqDTOS = Lists.newArrayList();
                for (MessageExt msg : msgs) {
                    String msgJson = new String(msg.getBody());
                    log.info("==> Consumer - Received message: {}", msgJson);
                    collectUnCollectNoteMqDTOS.add(JsonUtils.parseObject(msgJson, CollectUnCollectNoteMqDTO.class));
                }

                // 内存级操作合并
                Map<Long, List<CollectUnCollectNoteMqDTO>> groupMap = collectUnCollectNoteMqDTOS
                        .stream().collect(Collectors.groupingBy(CollectUnCollectNoteMqDTO::getUserId));

                List<CollectUnCollectNoteMqDTO> finalOperations = groupMap.values().stream()
                        .flatMap(userOperations -> {
                            Map<Long, List<CollectUnCollectNoteMqDTO>> noteGoupMap = userOperations
                                    .stream().collect(Collectors.groupingBy(CollectUnCollectNoteMqDTO::getNoteId));

                            return noteGoupMap.values().stream()
                                    .filter(operation -> {
                                        int size = operation.size();
                                        if (size % 2 == 0) {
                                            return false;
                                        } else {
                                            return true;
                                        }
                                    }).map(ops -> {
                                        return ops.get(ops.size() - 1);
                                    });
                        }).toList();
                if (CollUtil.isNotEmpty(finalOperations)) {
                    List<NoteCollectionDO> noteCollectionDOS = finalOperations.stream()
                            .map(finalOperation -> NoteCollectionDO.builder()
                                    .userId(finalOperation.getUserId())
                                    .noteId(finalOperation.getNoteId())
                                    .createTime(finalOperation.getCreateTime())
                                    .status(finalOperation.getType())
                                    .build())
                            .toList();
                    noteCollectionDOMapper.batchInsertOrUpdate(noteCollectionDOS);
                }
                // 手动 ACK，告诉 RocketMQ 这批次消息消费成功
                return ConsumeOrderlyStatus.SUCCESS;
            } catch (Exception e) {
                log.error("", e);
                // 这样 RocketMQ 会暂停当前队列的消费一段时间，再重试
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
            }
        });
        // 启动消费者
        consumer.start();
        return consumer;
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();  // 关闭消费者
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }


}
