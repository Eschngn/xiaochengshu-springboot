package com.chengliuxiang.xiaochengshu.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.exception.BizException;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.DateUtils;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.note.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.note.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.note.biz.domain.dataobject.NoteCollectionDO;
import com.chengliuxiang.xiaochengshu.note.biz.domain.dataobject.NoteDO;
import com.chengliuxiang.xiaochengshu.note.biz.domain.dataobject.NoteLikeDO;
import com.chengliuxiang.xiaochengshu.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.chengliuxiang.xiaochengshu.note.biz.domain.mapper.NoteDOMapper;
import com.chengliuxiang.xiaochengshu.note.biz.domain.mapper.NoteLikeDOMapper;
import com.chengliuxiang.xiaochengshu.note.biz.domain.mapper.TopicDOMapper;
import com.chengliuxiang.xiaochengshu.note.biz.enums.*;
import com.chengliuxiang.xiaochengshu.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.chengliuxiang.xiaochengshu.note.biz.model.vo.*;
import com.chengliuxiang.xiaochengshu.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.chengliuxiang.xiaochengshu.note.biz.rpc.KeyValueRpcService;
import com.chengliuxiang.xiaochengshu.note.biz.rpc.UserRpcService;
import com.chengliuxiang.xiaochengshu.note.biz.service.NoteService;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    /**
     * 笔记详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

    @Override
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        Integer type = publishNoteReqVO.getType();
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }
        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT:
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO:
                videoUri = publishNoteReqVO.getVideoUri();
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }
        String snowflakeId = distributedIdGeneratorRpcService.getSnowflakeId();
        String contentUuid = null;
        String content = publishNoteReqVO.getContent();
        boolean isContentEmpty = true; // 笔记内容是否为空，默认值为 true，即空

        // 如果用户填写了笔记内容
        if (StringUtils.isNotBlank(content)) {
            isContentEmpty = false;
            contentUuid = UUID.randomUUID().toString();
            boolean isSavedSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
            if (!isSavedSuccess) {
                throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }
        }
        Long topicId = publishNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
        }
        Long creatorId = LoginUserContextHolder.getUserId();

        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(snowflakeId))
                .title(publishNoteReqVO.getTitle())
                .isContentEmpty(isContentEmpty)
                .contentUuid(contentUuid)
                .creatorId(creatorId)
                .topicId(topicId)
                .topicName(topicName)
                .isTop(Boolean.FALSE)
                .type(type)
                .imgUris(imgUris)
                .videoUri(videoUri)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .build();

        try {
            noteDOMapper.insert(noteDO);
        } catch (Exception e) {
            log.error("==> 笔记存储失败", e);
            if (StringUtils.isNotBlank(contentUuid)) {
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
        }
        return Response.success();
    }

    @Override
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
        Long noteId = deleteNoteReqVO.getId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(selectNoteDO.getCreatorId(), currUserId)) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();
        int count = noteDOMapper.updateByPrimaryKeySelective(noteDO);
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        String noteDetailKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailKey);
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存消息发送成功");
        return Response.success();
    }

    @Override
    @SneakyThrows
    public Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO) {
        Long noteId = findNoteDetailReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();
        String noteDetailLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        // 命中本地缓存（包括 “null” 字符串）
        if (StringUtils.isNotBlank(noteDetailLocalCache)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailLocalCache, FindNoteDetailRspVO.class);
            if (Objects.nonNull(findNoteDetailRspVO)) {
                // 校验可见性
                checkNoteVisible(findNoteDetailRspVO.getVisible(), userId, findNoteDetailRspVO.getCreatorId());
                return Response.success(findNoteDetailRspVO);
            } else {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
        }
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);
        // 命中 Redis 缓存（包括 “null” 字符串）
        if (StringUtils.isNotBlank(noteDetailJson)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            if (Objects.nonNull(findNoteDetailRspVO)) {
                threadPoolTaskExecutor.submit(() -> {
                    LOCAL_CACHE.put(noteId, JsonUtils.toJsonString(findNoteDetailRspVO));
                });
                checkNoteVisible(findNoteDetailRspVO.getVisible(), userId, findNoteDetailRspVO.getCreatorId());
                return Response.success(findNoteDetailRspVO);
            } else {
                threadPoolTaskExecutor.submit(() -> {
                    LOCAL_CACHE.put(noteId, "null");
                });
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
        }

        // 如果缓存都没命中，查询数据库
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 如果笔记不存在
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.submit(() -> {
                // 保底1分钟的随机秒数
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(noteDetailRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        checkNoteVisible(noteDO.getVisible(), userId, noteDO.getCreatorId());

        Long creatorId = noteDO.getCreatorId();
        CompletableFuture<FindUserByIdRspDTO> userInfoFuture = CompletableFuture
                .supplyAsync(() -> userRpcService.findUserById(creatorId), threadPoolTaskExecutor);
        CompletableFuture<String> noteContentFuture = CompletableFuture.completedFuture(null);
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)) {
            noteContentFuture = CompletableFuture
                    .supplyAsync(() -> keyValueRpcService.findNoteContent(noteDO.getContentUuid()), threadPoolTaskExecutor);
        }

        CompletableFuture<String> finalNoteContentFuture = noteContentFuture;
        CompletableFuture<FindNoteDetailRspVO> resultFuture = CompletableFuture
                .allOf(userInfoFuture, noteContentFuture)
                .thenApply(s -> {
                    FindUserByIdRspDTO findUserByIdRspDTO = userInfoFuture.join();
                    String content = finalNoteContentFuture.join();
                    Integer noteType = noteDO.getType();
                    String imgUrisStr = noteDO.getImgUris();
                    List<String> imgUris = null;
                    if (Objects.equals(noteType, NoteTypeEnum.IMAGE_TEXT.getCode())
                            && StringUtils.isNotBlank(imgUrisStr)) {
                        imgUris = List.of(imgUrisStr.split(","));
                    }
                    return FindNoteDetailRspVO.builder()
                            .id(noteDO.getId())
                            .type(noteDO.getType())
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(noteDO.getCreatorId())
                            .creatorName(findUserByIdRspDTO.getNickName())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime())
                            .visible(noteDO.getVisible())
                            .build();
                });

        FindNoteDetailRspVO findNoteDetailRspVO = resultFuture.get();

        // 异步将笔记详情存入 Redis
        threadPoolTaskExecutor.submit(() -> {
            String noteDetail = JsonUtils.toJsonString(findNoteDetailRspVO);
            // 保底1天的随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            redisTemplate.opsForValue().set(noteDetailRedisKey, noteDetail, expireSeconds, TimeUnit.SECONDS);
        });
        return Response.success(findNoteDetailRspVO);
    }

    private void checkNoteVisible(Integer visible, Long currUserId, Long creatorId) {
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(creatorId, currUserId)) {
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }

    @Override
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        Long noteId = updateNoteReqVO.getId();
        Integer type = updateNoteReqVO.getType();
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }
        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT:
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO:
                videoUri = updateNoteReqVO.getVideoUri();
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }
        Long userId = LoginUserContextHolder.getUserId();
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        if (Objects.isNull(noteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        if (!Objects.equals(noteDO.getCreatorId(), userId)) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
        Long topicId = updateNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
            if (StringUtils.isBlank(topicName)) throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
        }

        // 第一次删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        // 更新笔记元数据表 t_note
        String content = updateNoteReqVO.getContent();
        noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(StringUtils.isBlank(content))
                .imgUris(imgUris)
                .title(updateNoteReqVO.getTitle())
                .topicId(updateNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(videoUri)
                .build();
        noteDOMapper.updateByPrimaryKey(noteDO);

        // 延迟双删策略：延迟第二次删除 Redis 缓存，保持数据一致性
        // 异步发送延时消息
        Message<String> message = MessageBuilder.withPayload(String.valueOf(noteId)).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE, message,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("## 延时删除 Redis 笔记缓存消息发送成功");
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.error("## 延时删除 Redis 笔记缓存消息发送失败", throwable);
                    }
                },
                3000, // 超时时间(毫秒)
                1 // 延迟级别，1 表示延时 1s
        );

        // LOCAL_CACHE.invalidate(noteId);

        // 同步发送广播模式 MQ，将所有部署示例的本地缓存全部删除
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ:删除笔记本地缓存消息发送成功");

        NoteDO selectedNote = noteDOMapper.selectByPrimaryKey(noteId);
        String contentUuid = selectedNote.getContentUuid();

        // 笔记内容是否更新成功
        boolean isUpdateContentSuccess = false;
        if (StringUtils.isBlank(content)) {
            // 若笔记内容为空，则删除 K-V 存储
            isUpdateContentSuccess = keyValueRpcService.deleteNoteContent(contentUuid);
        } else {
            // 若将无内容的笔记，更新为了有内容的笔记，需要重新生成 UUID
            contentUuid = StringUtils.isBlank(contentUuid) ? UUID.randomUUID().toString() : contentUuid;
            // 调用 K-V 更新短文本
            isUpdateContentSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
        }
        // 如果更新失败，抛出业务异常，回滚事务
        if (!isUpdateContentSuccess) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }
        return Response.success();
    }

    @Override
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }

    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(selectNoteDO.getCreatorId(), currUserId)) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .updateTime(LocalDateTime.now())
                .build();
        int count = noteDOMapper.updateVisibleOnlyMe(noteDO);
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功");

        return Response.success();
    }

    @Override
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        Long noteId = topNoteReqVO.getId();
        Boolean isTop = topNoteReqVO.getIsTop();
        Long currUserId = LoginUserContextHolder.getUserId();
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(isTop)
                .updateTime(LocalDateTime.now())
                .creatorId(currUserId)
                .build();
        int count = noteDOMapper.updateIsTop(noteDO);
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功");

        return Response.success();
    }

    @Override
    public Response<?> likeNote(LikeNoteReqVO likeNoteReqVO) {
        Long noteId = likeNoteReqVO.getId();
        checkNoteIsExist(noteId);
        Long userId = LoginUserContextHolder.getUserId();
        String bloomUserNoteLikeListRedisKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_like_check.lua")));
        Long result = redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListRedisKey), noteId);
        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);
        // 用户点赞列表 ZSet Key
        String userNoteLikeZSetRedisKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        switch (Objects.requireNonNull(noteLikeLuaResultEnum)) {
            case NOT_EXIST -> { // Redis 中该用户的布隆过滤器不存在
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                if (count > 0) {
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() -> batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListRedisKey));
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // 目标笔记未被点赞，先同步该用户其他已点赞笔记到布隆过滤器
                batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListRedisKey);
                // 再添加当前目标笔记到布隆过滤器
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_add_note_like_and_expire.lua")));
                script.setResultType(Long.class);
                redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListRedisKey), noteId, expireSeconds);
            }
            case NOTE_LIKED -> { // 布隆过滤器判断已点赞存在误判，需要进一步校验
                Double score = redisTemplate.opsForZSet().score(userNoteLikeZSetRedisKey, noteId);
                if (Objects.nonNull(score)) {
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // 若 Score 为空，则 Redis 中用户点赞笔记列表 ZSet 不存在或者 ZSet 中不存在该笔记 ID（前 100 篇），查询数据库校验
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                // TODO 还有一种情况：如果用户一直点赞的都是自己已点赞笔记列表中100篇后的笔记，这种情况即使 Redis 中有 ZSet 列表，也都会走数据库校验
                if (count > 0) {
                    // 如果数据库中有该笔记点赞记录，则需要异步初始化 ZSet，防止请求都打到数据库
                    asynInitUserNoteLikesZSet(userId, userNoteLikeZSetRedisKey);
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
            }
        }
        // 以下代码只有目标笔记真的未被点赞的情况才会走到
        LocalDateTime now = LocalDateTime.now();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/note_like_check_and_update_zset.lua")));
        script.setResultType(Long.class);
        result = redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetRedisKey), noteId,
                LikeUnlikeNoteTypeEnum.LIKE.getCode(), DateUtils.localDateTime2Timestamp(now));
        if (Objects.equals(result, NoteLikeLuaResultEnum.NOT_EXIST.getCode())) {
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserIdAndLimit(userId, 100);
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/batch_add_note_like_zset_and_expire.lua")));
            script2.setResultType(Long.class);
            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetRedisKey), luaArgs);
                // 再次调用 script 脚本，将最新的点赞的笔记添加到 ZSet 中
                result = redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetRedisKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else { // 数据库中无该用户的点赞记录，直接将当前点赞的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now()));
                luaArgs.add(noteId);
                luaArgs.add(expireSeconds);
                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetRedisKey), luaArgs.toArray());
            }
        }

        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode())
                .createTime(now)
                .build();
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO)).build();
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记点赞】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    private void checkNoteIsExist(Long noteId) {
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
        if (Objects.isNull(findNoteDetailRspVO)) {
            // 如果本地缓存没有，则从 Redis 中检查
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
            String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);
            findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            if (Objects.isNull(findNoteDetailRspVO)) {
                // Redis 中也没有，则从数据库中查询
                int count = noteDOMapper.selectCountByNoteId(noteId);
                if (count == 0) {
                    throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }
                threadPoolTaskExecutor.submit(() -> {
                    FindNoteDetailReqVO findNoteDetailReqVO = FindNoteDetailReqVO.builder().id(noteId).build();
                    findNoteDetail(findNoteDetailReqVO);
                });
            }
        }
    }

    private void batchAddNoteLike2BloomAndExpire(Long userId, Long expireSeconds, String bloomUserNoteLikeListRedisKey) {
        threadPoolTaskExecutor.submit(() -> {
            try {
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);
                if (CollUtil.isNotEmpty(noteLikeDOS)) {
                    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                    script.setResultType(Long.class);
                    script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_batch_add_note_like_and_expire.lua")));
                    List<Object> luaArgs = Lists.newArrayList();
                    noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId()));
                    luaArgs.add(expireSeconds);
                    redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListRedisKey), luaArgs.toArray());
                }
            } catch (Exception e) {
                log.error("## 异步初始化布隆过滤器异常: ", e);
            }
        });
    }

    private Object[] buildNoteLikeZSetLuaArgs(List<NoteLikeDO> noteLikeDOS, Long expireSeconds) {
        int argsLength = noteLikeDOS.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];
        int i = 0;
        for (NoteLikeDO noteLikeDO : noteLikeDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteLikeDO.getCreateTime());
            luaArgs[i + 1] = noteLikeDO.getNoteId();
            i += 2;
        }
        luaArgs[argsLength - 1] = expireSeconds;
        return luaArgs;
    }

    private void asynInitUserNoteLikesZSet(Long userId, String userNoteLikeZSetRedisKey) {
        threadPoolTaskExecutor.execute(() -> {
            Boolean hasKey = redisTemplate.hasKey(userNoteLikeZSetRedisKey);
            if (!hasKey) { // Redis在中不存在该用户的点赞笔记列表，则进行初始化
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserIdAndLimit(userId, 100);
                if (CollUtil.isNotEmpty(noteLikeDOS)) {
                    long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/batch_add_note_like_zset_and_expire.lua")));
                    script2.setResultType(Long.class);
                    redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetRedisKey), luaArgs);
                }
            }
        });
    }

    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        Long noteId = unlikeNoteReqVO.getId();
        checkNoteIsExist(noteId);
        Long userId = LoginUserContextHolder.getUserId();
        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_note_unlike_check.lua")));
        Long result = redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), noteId);
        NoteUnlikeLuaResultEnum noteUnlikeLuaResultEnum = NoteUnlikeLuaResultEnum.valueOf(result);
        switch (Objects.requireNonNull(noteUnlikeLuaResultEnum)) {
            case NOT_EXIST -> {
                // 异步初始化布隆过滤器
                threadPoolTaskExecutor.submit(() -> {
                    long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListKey);
                });
                // 从数据库中校验笔记是否被点赞
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
            case NOTE_LIKED -> {
                // 布隆过滤器存在时，当用户点赞了该笔记，且在布隆过滤器过期之前，又取消了点赞，会存在误判（此时的未点赞判断成了已点赞）
                // 需要从数据库中校验笔记是否被点赞
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
            // 布隆过滤器校验目标笔记未被点赞（返回为0，判断绝对正确）
            case NOTE_NOT_LIKED -> throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
        }
        // 能走到这里说明目标笔记一定是真实的已点赞的状态
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(("/lua/note_like_check_and_update_zset.lua"))));
        script.setResultType(Long.class);
        result = redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, LikeUnlikeNoteTypeEnum.UNLIKE.getCode());
        if (Objects.equals(result, NoteLikeLuaResultEnum.NOT_EXIST.getCode())) {
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserIdAndLimit(userId, 100);
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/batch_add_note_like_zset_and_expire.lua")));
            script2.setResultType(Long.class);
            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);
                redisTemplate.opsForZSet().remove(userNoteLikeZSetKey, noteId);
            }
        }

        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.UNLIKE.getCode())
                .createTime(LocalDateTime.now())
                .build();
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO)).build();
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    @Override
    public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        Long noteId = collectNoteReqVO.getId();
        checkNoteIsExist(noteId);
        Long userId = LoginUserContextHolder.getUserId();
        String userNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectListKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bloom_note_collect_check.lua")));
        Long result = redisTemplate.execute(script, Collections.singletonList(userNoteCollectListKey), noteId);
        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(result);
        switch (noteCollectLuaResultEnum) {
            case NOT_EXIST -> {
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                if (count > 0) {
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, userNoteCollectListKey));
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }
                // 若目标笔记未被收藏，查询当前用户是否有收藏其他笔记，有则同步初始化布隆过滤器
                batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, userNoteCollectListKey);
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_note_collect_and_expire.lua")));
                script.setResultType(Long.class);
                redisTemplate.execute(script, Collections.singletonList(userNoteCollectListKey), noteId, expireSeconds);
            }
            case NOTE_COLLECTED -> {

            }
        }
        return Response.success();
    }

    private void batchAddNoteCollect2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteCollectListKey) {
        try {
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectByUserId(userId);
            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setResultType(Long.class);
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_collect_and_expire.lua")));
                List<Object> luaArgs = Lists.newArrayList();
                noteCollectionDOS.forEach(noteCollectionDO -> luaArgs.add(noteCollectionDO.getNoteId()));
                luaArgs.add(expireSeconds);
                redisTemplate.execute(script, Collections.singletonList(bloomUserNoteCollectListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】布隆过滤器异常: ", e);
        }
    }


}
