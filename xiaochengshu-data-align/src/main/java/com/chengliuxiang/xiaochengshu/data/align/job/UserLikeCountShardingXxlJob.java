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
public class UserLikeCountShardingXxlJob {

    @Resource
    private SelectRecordMapper selectRecordMapper;
    @Resource
    private UpdateRecordMapper updateRecordMapper;
    @Resource
    private DeleteRecordMapper deleteRecordMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @XxlJob("userLikeCountShardingJobHandler")
    public void userLikeCountShardingJobHandler(){
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        XxlJobHelper.log("===========> 开始定时分片广播任务：对昨日发生变更的用户获得点赞数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
        String date = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);
        int batchSize = 1000; // 一批次 1000 条
        int processedTotal = 0; // 共对齐了多少条记录，默认为 0
        for (; ; ) {
            List<Long> userIds = selectRecordMapper.selectBatchFromDataAlignUserLikeCountTempTable(tableNameSuffix, batchSize);
            if (CollUtil.isEmpty(userIds)) break;
            userIds.forEach(userId -> {
                int likeTotal = selectRecordMapper.selectUserLikeCountFromNoteLikeAndNoteTableByUserId(userId);
                int count = updateRecordMapper.updateUserLikeTotalByUserId(userId, likeTotal);
                if (count > 0) {
                    String countNoteKey = RedisKeyConstants.buildCountUserKey(userId);
                    boolean isExisted = redisTemplate.hasKey(countNoteKey);
                    if (isExisted) {
                        redisTemplate.opsForHash().put(countNoteKey, RedisKeyConstants.FIELD_LIKE_TOTAL, likeTotal);
                    }
                }

            });
            deleteRecordMapper.batchDeleteDataAlignUserLikeCountTempTable(tableNameSuffix, userIds);
            processedTotal += userIds.size();
        }
        XxlJobHelper.log("===========> 结束定时分片广播任务：对昨日发生变更的用户获得点赞数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
