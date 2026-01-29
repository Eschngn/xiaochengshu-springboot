package com.chengliuxiang.xiaochengshu.data.align.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class CreateJobXxlJob {

    @XxlJob("createTableJobHandler")
    public void createTableJobHandler(){
        XxlJobHelper.log("## 开始初始化明日增量数据表...");
    }
}
