package com.chengliuxiang.xiaochengshu.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.exception.BizException;
import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.DateUtils;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.user.relation.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.dataobject.FollowingDO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.chengliuxiang.xiaochengshu.user.relation.biz.enums.LuaResultEnum;
import com.chengliuxiang.xiaochengshu.user.relation.biz.enums.ResponseCodeEnum;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.dto.UnfollowUserMqDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FindFollowingListReqVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FindFollowingUserRspVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FollowUserReqVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.UnfollowUserReqVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.rpc.UserRpcService;
import com.chengliuxiang.xiaochengshu.user.relation.biz.service.RelationService;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class RelationServiceImpl implements RelationService {
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

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
        script.setResultType(Long.class);
        // 当前时间戳
        long timestamp = DateUtils.localDateTime2Timestamp(LocalDateTime.now());
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
        checkLuaScriptResult(result);
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
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
        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                .userId(currentUserId)
                .followUserId(followUserId)
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(followUserMqDTO)).build();
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;
        String hashKey = String.valueOf(currentUserId);
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.equals(unfollowUserId, userId)) {
            throw new BizException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }
        // 校验取关的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findUserById(unfollowUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.UNFOLLOW_USER_NOT_EXISTED);
        }
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/unfollow_check_and_delete.lua")));
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);
        if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
            throw new BizException(ResponseCodeEnum.NOT_FOLLOWED); // 未关注，无法取关
        }
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) { // ZSET 关注列表不存在
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            if (CollUtil.isEmpty(followingDOS)) {
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            } else { // 若数据库中关注记录存在，则全量同步到 Redis 中，并设置过期时间
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);
                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                script2.setResultType(Long.class);
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                redisTemplate.execute(script, Collections.singletonList(followingRedisKey), luaArgs);
                // 再次调用上面的 Lua 脚本，将取关的用户从 zset 列表中移除
                result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);
                if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) { // 再次校验结果
                    throw new BizException(ResponseCodeEnum.NOT_FOLLOWED); // 取关的用户不在关注列表
                }
            }
        }

        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                .userId(userId)
                .unfollowUserId(unfollowUserId)
                .createTime(LocalDateTime.now())
                .build();
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unfollowUserMqDTO)).build();
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_UNFOLLOW;
        String hashKey = String.valueOf(userId);
        log.info("==> 开始发送取关操作 MQ，消息体:{}", unfollowUserMqDTO);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.info("==> MQ 发送异常:", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 构建 Lua 脚本参数
     *
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
     *
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

    @Override
    public PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO) {
        Long userId = findFollowingListReqVO.getUserId();
        Integer pageNo = findFollowingListReqVO.getPageNo();
        // 先从 Redis 中查
        String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        Long totalCount = redisTemplate.opsForZSet().zCard(followingListRedisKey);
        List<FindFollowingUserRspVO> findFollowingUserRspVOS = Lists.newArrayList();
        long pageSize = 10; // 每页展示 10 条数据
        if (totalCount != null && totalCount > 0) { // Redis 中有数据
            long totalPage = PageResponse.getTotalPage(totalCount, pageSize); // 总页数
            if (pageNo > totalPage) return PageResponse.success(null, pageNo, totalCount);

            long offset = PageResponse.getOffset(pageNo, pageSize);
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(followingListRedisKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, pageSize);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRspVOS);
            }
        } else { // Redis 中没有数据 则从数据库查询
            long count = followingDOMapper.selectCountByUserId(userId);
            long totalPage = PageResponse.getTotalPage(count, pageSize);
            if (pageNo > totalPage) return PageResponse.success(null, pageNo, totalCount);
            long offset = PageResponse.getOffset(pageNo, pageSize);
            List<FollowingDO> followingDOS = followingDOMapper.selectPageListByUserId(userId, offset, pageSize);
            totalCount = count;
            if(CollUtil.isNotEmpty(followingDOS)){
                List<Long> userIds = followingDOS.stream().map(FollowingDO::getFollowingUserId).toList();
                findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRspVOS);
                // 异步将关注列表全量同步到 Redis 中
                threadPoolTaskExecutor.submit(()->syncFollowingList2Redis(userId));
            }
        }
        return PageResponse.success(findFollowingUserRspVOS, pageNo, totalCount);
    }

    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(List<Long> userIds, List<FindFollowingUserRspVO> findFollowingUserRspVOS) {
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFollowingUserRspVOS = findUserByIdRspDTOS.stream()
                    .map(dto -> FindFollowingUserRspVO.builder()
                            .userId(dto.getId())
                            .nickname(dto.getNickName())
                            .avatar(dto.getAvatar())
                            .introduction(dto.getIntroduction())
                            .build()).toList();
        }
        return findFollowingUserRspVOS;
    }

    /**
     * 全量同步关注列表至 Redis 中
     */
    private void syncFollowingList2Redis(Long userId) {
        List<FollowingDO> followingDOS = followingDOMapper.selectAllByUserId(userId);
        if(CollUtil.isNotEmpty(followingDOS)){
            String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            Object[] luaArgs=buildLuaArgs(followingDOS, expireSeconds);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(followingListRedisKey), luaArgs);
        }
    }
}
