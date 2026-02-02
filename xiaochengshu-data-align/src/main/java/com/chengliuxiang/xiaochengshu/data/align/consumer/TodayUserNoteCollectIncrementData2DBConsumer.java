package com.chengliuxiang.xiaochengshu.data.align.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.data.align.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.data.align.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.data.align.constant.TableConstants;
import com.chengliuxiang.xiaochengshu.data.align.domain.mapper.InsertRecordMapper;
import com.chengliuxiang.xiaochengshu.data.align.model.dto.CollectUnCollectNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_data_align_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT,
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE)
@Slf4j
public class TodayUserNoteCollectIncrementData2DBConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private InsertRecordMapper insertRecordMapper;
    @Value("${table.shards}")
    private int tableShards;

    @Override
    public void onMessage(String body) {
        log.info("## TodayUserNoteCollectIncrementData2DBConsumer 消费到了 MQ: {}", body);
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = JsonUtils.parseObject(body, CollectUnCollectNoteMqDTO.class);
        if (Objects.isNull(collectUnCollectNoteMqDTO)) return;
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        Long noteCreatorId = collectUnCollectNoteMqDTO.getNoteCreatorId();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD',KEYS[1],ARGV[1])", Long.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_note_collect_check.lua")));
        script.setResultType(Long.class);
        Long result = null;

        // ------------------------- 笔记的收藏数变更记录 ---------------------------
        String noteBloomKey = RedisKeyConstants.buildBloomNoteCollectListKey(date);
        result = redisTemplate.execute(script, Collections.singletonList(noteBloomKey), noteId);
        if (Objects.equals(result, 0L)) {  // 若布隆过滤器判断不存在（绝对正确）
            long noteIdHashKey = noteId % tableShards;
            try {
                insertRecordMapper.insert2DataAlignNoteCollectCountTempTable(TableConstants.buildTableNameSuffix(date, noteIdHashKey), noteId);
            } catch (Exception e) {
                log.error("", e);
            }
            redisTemplate.execute(bloomAddScript, Collections.singletonList(noteBloomKey), noteId);
        }

        // ------------------------- 笔记发布者获得的收藏数变更记录 ---------------------------
        String userBloomKey = RedisKeyConstants.buildBloomUserCollectListKey(date);
        result = redisTemplate.execute(script, Collections.singletonList(userBloomKey), noteCreatorId);
        if (Objects.equals(result, 0L)) {
            long userIdHashKey = noteCreatorId % tableShards;
            try {
                insertRecordMapper.insert2DataAlignUserCollectCountTempTable(TableConstants.buildTableNameSuffix(date, userIdHashKey), noteCreatorId);
            } catch (Exception e) {
                log.error("", e);
            }
            redisTemplate.execute(bloomAddScript, Collections.singletonList(userBloomKey), noteCreatorId);
        }

    }
}
