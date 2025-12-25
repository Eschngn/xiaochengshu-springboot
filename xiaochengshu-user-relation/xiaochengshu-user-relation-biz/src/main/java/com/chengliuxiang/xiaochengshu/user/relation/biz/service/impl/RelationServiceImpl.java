package com.chengliuxiang.xiaochengshu.user.relation.biz.service.impl;

import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.exception.BizException;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.DateUtils;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.user.relation.biz.enums.LuaResultEnum;
import com.chengliuxiang.xiaochengshu.user.relation.biz.enums.ResponseCodeEnum;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FollowUserReqVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.rpc.UserRpcService;
import com.chengliuxiang.xiaochengshu.user.relation.biz.service.RelationService;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

@Service
public class RelationServiceImpl implements RelationService {
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        Long followUserId = followUserReqVO.getFollowUserId();
        Long currentUserId= LoginUserContextHolder.getUserId();
        if(Objects.equals(followUserId,currentUserId)){
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findUserById(followUserId);
        if(Objects.isNull(findUserByIdRspDTO)){
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }
        // 当前用户关注列表的 redis key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(currentUserId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));
        // Lua 脚本路径
        script.setResultType(Long.class);
        // 当前时间戳
        long timestamp= DateUtils.localDateTime2Timestamp(LocalDateTime.now());
        Long result=redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
        LuaResultEnum luaResultEnum=LuaResultEnum.valueOf(result);
        if(Objects.isNull(luaResultEnum)){
            throw new RuntimeException("Lua 返回结果错误");
        }
        switch (luaResultEnum){
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT); // 关注达到上限
            case ALREADY_FOLLOWED ->  throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED); // 已关注了该用户
            case ZSET_NOT_EXIST -> { // ZSET 关注列表不存在

            }
        }
        return Response.success();
    }
}
