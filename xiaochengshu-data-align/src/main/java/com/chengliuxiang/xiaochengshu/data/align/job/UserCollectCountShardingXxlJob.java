package com.chengliuxiang.xiaochengshu.data.align.job;

import cn.hutool.core.collection.CollUtil;
import com.chengliuxiang.xiaochengshu.data.align.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.data.align.constant.TableConstants;
import com.chengliuxiang.xiaochengshu.data.align.domain.mapper.DeleteRecordMapper;
import com.chengliuxiang.xiaochengshu.data.align.domain.mapper.SelectRecordMapper;
import com.chengliuxiang.xiaochengshu.data.align.domain.mapper.UpdateRecordMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class UserCollectCountShardingXxlJob {
    @Resource
    private SelectRecordMapper selectRecordMapper;
    @Resource
    private DeleteRecordMapper deleteRecordMapper;
    @Resource
    private UpdateRecordMapper updateRecordMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @XxlJob("userCollectCountShardingJobHandler")
    public void userCollectCountShardingJobHandler() {
        int shardTotal = XxlJobHelper.getShardTotal();
        int shardIndex = XxlJobHelper.getShardIndex();
        XxlJobHelper.log("===========> 开始定时分片广播任务：对昨日发生变更的用户获得收藏数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号={},总分片数={}", shardIndex, shardTotal);
        log.info("分片参数：当前分片序号={},总分片数={}", shardIndex, shardTotal);
        String date = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);
        int batchSize = 1000; // 一批次 1000 条
        int processedTotal = 0; // 共对齐了多少条记录，默认为 0
        for (; ; ) {
            List<Long> userIds = selectRecordMapper.selectBatchFromDataAlignUserCollectCountTempTable(tableNameSuffix, batchSize);
            if (CollUtil.isEmpty(userIds)) break;
            userIds.forEach(userId -> {
                int collectTotal = selectRecordMapper.selectCountFromUserCollectTableByNoteId(userId);
                int count = updateRecordMapper.updateUserCollectTotalByUserId(userId, collectTotal);
                if (count > 0) {
                    String countUserKey = RedisKeyConstants.buildCountUserKey(userId);
                    boolean isExisted = redisTemplate.hasKey(countUserKey);
                    if (isExisted) {
                        redisTemplate.opsForHash().put(countUserKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, collectTotal);
                    }
                }
            });
            deleteRecordMapper.batchDeleteDataAlignUserCollectCountTempTable(tableNameSuffix, userIds);
        }
        XxlJobHelper.log("===========> 结束定时分片广播任务：对昨日发生变更的用户获得收藏数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
