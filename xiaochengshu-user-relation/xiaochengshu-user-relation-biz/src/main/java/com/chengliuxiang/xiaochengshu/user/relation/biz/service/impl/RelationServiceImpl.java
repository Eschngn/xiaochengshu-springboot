package com.chengliuxiang.xiaochengshu.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.exception.BizException;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.DateUtils;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.dataobject.FollowingDO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.mapper.FollowingDOMapper;
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
import java.util.List;
import java.util.Objects;

@Service
public class RelationServiceImpl implements RelationService {
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private FollowingDOMapper followingDOMapper;

    /**
     * 关注用户
     *
     * @param followUserReqVO
     * @return
     */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        Long followUserId = followUserReqVO.getFollowUserId();
        Long currentUserId = LoginUserContextHolder.getUserId();
        if (Objects.equals(followUserId, currentUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findUserById(followUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
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
        long timestamp = DateUtils.localDateTime2Timestamp(LocalDateTime.now());
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);
        if (Objects.isNull(luaResultEnum)) {
            throw new RuntimeException("Lua 返回结果错误");
        }
        switch (luaResultEnum) {
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT); // 关注达到上限
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED); // 已关注了该用户
            case ZSET_NOT_EXIST -> { // ZSET 关注列表不存在
                List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(currentUserId);
                // 随机过期时间 保底1天+随机秒数
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                if (CollUtil.isEmpty(followingDOS)) { // 若数据库中记录为空，直接 ZADD 关系数据, 并设置过期时间
                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                    script2.setResultType(Long.class);
                    // TODO: 可以根据用户类型，设置不同的过期时间，若当前用户为大V, 则可以过期时间设置的长些或者不设置过期时间；如不是，则设置的短些
                    // 如何判断呢？可以从计数服务获取用户的粉丝数，目前计数服务还没创建，则暂时采用统一的过期策略
                    redisTemplate.execute(script2, Collections.singletonList(followingRedisKey), followUserId, timestamp, expireSeconds);
                } else { // 若数据库中记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                    Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);
                    DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                    script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                    script3.setResultType(Long.class);
                    redisTemplate.execute(script3, Collections.singletonList(followingRedisKey), luaArgs);
                    // 再次调用上面的 Lua 脚本：follow_check_and_add.lua , 将最新的关注关系添加进去
                    result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
                    checkLuaScriptResult(result);
                }
            }
        }
        return Response.success();
    }

    /**
     * 构建 Lua 脚本参数
     * @param followingDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildLuaArgs(List<FollowingDO> followingDOS, long expireSeconds) {
        int argsLength = followingDOS.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];
        int i = 0;
        for (FollowingDO followingDO : followingDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(followingDO.getCreateTime()); // 关注时间作为 score
            luaArgs[i + 1] = followingDO.getFollowingUserId(); // 关注的用户 ID 作为 ZSet value
            i += 2;
        }
        luaArgs[argsLength - 1] = expireSeconds;
        return luaArgs;
    }

    /**
     * 校验 Lua 脚本结果，根据状态码抛出对应的业务异常
     * @param result
     */
    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }
}
