package com.chengliuxiang.xiaochengshu.data.align.job;

import com.chengliuxiang.xiaochengshu.data.align.constant.TableConstants;
import com.chengliuxiang.xiaochengshu.data.align.domain.mapper.CreateTableMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RefreshScope
public class CreateTableXxlJob {

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int shards;

    @Resource
    private CreateTableMapper createTableMapper;

    @XxlJob("createTableJobHandler")
    public void createTableJobHandler() {
        String date = LocalDate.now().plusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        XxlJobHelper.log("## 开始创建日增量数据表，日期:{}...", date);
        if (shards > 0) {
            for (int hashKey = 0; hashKey < shards; hashKey++) {
                String tableNameSuffix = TableConstants.buildTableNameSuffix(date, hashKey);
                createTableMapper.createDataAlignFollowingCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignFollowingCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNotePublishCountTempTable(tableNameSuffix);
            }
        }
        XxlJobHelper.log("## 结束创建日增量数据表，日期：{}...", date);
    }
}
