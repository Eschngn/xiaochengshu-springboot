package com.chengliuxiang.xiaochengshu.data.align.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.data.align.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.data.align.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.data.align.constant.TableConstants;
import com.chengliuxiang.xiaochengshu.data.align.domain.mapper.InsertRecordMapper;
import com.chengliuxiang.xiaochengshu.data.align.model.dto.FollowUnfollowMqDTO;
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
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group_data_align_" + MQConstants.TOPIC_COUNT_FOLLOWING,
        topic = MQConstants.TOPIC_COUNT_FOLLOWING)
@Slf4j
public class TodayUserFollowIncrementData2DBConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private InsertRecordMapper insertRecordMapper;

    @Value("${table.shards}")
    private int tableShards;

    @Override
    public void onMessage(String body) {
        log.info("## TodayUserFollowIncrementData2DBConsumer 消费到了 MQ: {}", body);

        FollowUnfollowMqDTO followUnfollowMqDTO = JsonUtils.parseObject(body, FollowUnfollowMqDTO.class);
        if (Objects.isNull(followUnfollowMqDTO)) return;

        // 关注/取关操作的源用户和目标用户 ID
        Long userId = followUnfollowMqDTO.getUserId();
        Long targetUserId = followUnfollowMqDTO.getTargetUserId();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_follow_check.lua")));
        script.setResultType(Long.class);
        Long result = null;
        RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD',KEYS[1],ARGV[1])", Long.class);

        // ------------------------- 源用户的关注数变更记录 -------------------------
        String userFollowBloomKey = RedisKeyConstants.buildBloomUserFollowListKey(date);
        result = redisTemplate.execute(script, Collections.singletonList(userFollowBloomKey), userId);
        if (Objects.equals(result, 0L)) { // 若布隆过滤器判断不存在（绝对正确）
            long userIdHashKey = userId % tableShards;
            try {
                insertRecordMapper.insert2DataAlignUserFollowingCountTempTable(TableConstants.buildTableNameSuffix(date, userIdHashKey), userId);
            } catch (Exception e) {
                log.error("", e);
            }
            redisTemplate.execute(bloomAddScript,Collections.singletonList(userFollowBloomKey),userId);
        }

        // ------------------------- 目标用户的粉丝数变更记录 -------------------------
        String userFansBloomKey = RedisKeyConstants.buildBloomUserFansListKey(date);
        result = redisTemplate.execute(script, Collections.singletonList(userFansBloomKey), targetUserId);
        if (Objects.equals(result, 0L)) { // 若布隆过滤器判断不存在（绝对正确）
            long targetUserIdHashKey = targetUserId % tableShards;
            try {
                insertRecordMapper.insert2DataAlignUserFansCountTempTable(TableConstants.buildTableNameSuffix(date, targetUserIdHashKey), targetUserId);
            } catch (Exception e) {
                log.error("", e);
            }
            redisTemplate.execute(bloomAddScript,Collections.singletonList(userFansBloomKey),targetUserId);
        }

    }
}
