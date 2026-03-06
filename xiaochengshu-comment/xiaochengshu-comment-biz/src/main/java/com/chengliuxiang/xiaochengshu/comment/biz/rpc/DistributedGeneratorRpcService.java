package com.chengliuxiang.xiaochengshu.comment.biz.rpc;

import com.chengliuxiang.xiaochengshu.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class DistributedGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 生成评论 ID
     * @return
     */
    public String generateCommentId(){
        return distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-comment-id");
    }
}
