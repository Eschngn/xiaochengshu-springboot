package com.chengliuxiang.xiaochengshu.user.relation.biz.consumer;

import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.user.relation.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.dataobject.FansDO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.dataobject.FollowingDO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.mapper.FansDOMapper;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.dto.FollowUserMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "xiaochengshu_group",
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW)
@Slf4j
public class FollowUnfollowConsumer implements RocketMQListener<Message> {
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private FansDOMapper fansDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void onMessage(Message message) {
        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();
        log.info("==> FollowUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        if(Objects.equals(tags, MQConstants.TAG_FOLLOW)){ // 关注
            handleFollowTagMessage(bodyJsonStr);
        }else if(Objects.equals(tags, MQConstants.TAG_UNFOLLOW)){ // 取关

        }
    }

    private void handleFollowTagMessage(String bodyJsonStr) {
        FollowUserMqDTO followUserMqDTO = JsonUtils.parseObject(bodyJsonStr, FollowUserMqDTO.class);
        if(Objects.isNull(followUserMqDTO)) return;
        // 幂等性：通过联合唯一索引保证
        Long userId = followUserMqDTO.getUserId();
        Long followUserId = followUserMqDTO.getFollowUserId();
        LocalDateTime createTime = followUserMqDTO.getCreateTime();
        // 编程式提交事务
        boolean isSuccess=Boolean.TRUE.equals(transactionTemplate.execute(status ->{
            try {
                int count = followingDOMapper.insert(FollowingDO.builder()
                        .userId(userId)
                        .followingUserId(followUserId)
                        .createTime(createTime).build());
                if(count > 0){
                    fansDOMapper.insert(FansDO.builder()
                            .userId(followUserId)
                            .fansUserId(userId)
                            .createTime(createTime).build());
                }
                return true;
            }catch (Exception e){
                status.setRollbackOnly(); // 标记事务为回滚
                log.error("",e);
            }
            return false;
        }));
        log.info("## 数据库添加记录结果：{}", isSuccess);
        // TODO: 更新 Redis 中被关注用户的 ZSet 粉丝列表
    }
}
