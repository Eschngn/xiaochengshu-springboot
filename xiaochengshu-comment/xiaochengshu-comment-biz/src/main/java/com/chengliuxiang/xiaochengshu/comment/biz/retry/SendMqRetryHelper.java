package com.chengliuxiang.xiaochengshu.comment.biz.retry;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class SendMqRetryHelper {

    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private RetryTemplate retryTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;


    /**
     * 异步发送 MQ
     *
     * @param topic
     * @param body
     */
    public void asyncSend(String topic, String body) {
        log.info("==> 开始异步发送 MQ, Topic: {}, Body: {}", topic, body);
        Message<String> message = MessageBuilder.withPayload(body).build();

        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论发布】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论发布】MQ 发送异常: ", throwable);
                handleRetry(topic, message);
            }
        });
    }

    /**
     * 重试处理
     *
     * @param topic
     * @param message
     */
    private void handleRetry(String topic, Message<String> message) {
        threadPoolTaskExecutor.execute(() -> {
            try {
                // 通过 retryTemplate 执行重试
                retryTemplate.execute((RetryCallback<Void, RuntimeException>) context -> {
                    log.info("==> 开始重试 MQ 发送, 当前重试次数: {}, 时间: {}", context.getRetryCount() + 1, LocalDateTime.now());
                    // 同步发送 MQ
                    rocketMQTemplate.syncSend(topic, message);
                    return null;
                });
            } catch (Exception e) {
                // 多次重试失败，进入兜底方案
                fallback(e, topic, message.getPayload());
            }
        });
    }


    /**
     * 兜底方案: 将发送失败的 MQ 写入数据库，之后，通过定时任务扫表，将发送失败的 MQ 再次发送，最终发送成功后，将该记录物理删除
     */
    public void fallback(Exception e, String topic, String body) {
        log.error("==> 多次发送失败, 进入兜底方案, Topic: {}, bodyJson: {}", topic, body);
        // TODO
    }
}
