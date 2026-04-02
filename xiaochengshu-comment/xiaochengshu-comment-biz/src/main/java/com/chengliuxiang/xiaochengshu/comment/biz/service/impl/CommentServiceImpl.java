package com.chengliuxiang.xiaochengshu.comment.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.constant.DateConstants;
import com.chengliuxiang.framework.common.exception.BizException;
import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.DateUtils;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.comment.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.comment.biz.constant.RedisKeyConstants;
import com.chengliuxiang.xiaochengshu.comment.biz.domain.dataobject.CommentDO;
import com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper.CommentDOMapper;
import com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper.NoteCountDOMapper;
import com.chengliuxiang.xiaochengshu.comment.biz.enums.ResponseCodeEnum;
import com.chengliuxiang.xiaochengshu.comment.biz.model.dto.PublishCommentMqDTO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.*;
import com.chengliuxiang.xiaochengshu.comment.biz.retry.SendMqRetryHelper;
import com.chengliuxiang.xiaochengshu.comment.biz.rpc.DistributedGeneratorRpcService;
import com.chengliuxiang.xiaochengshu.comment.biz.rpc.KeyValueRpcService;
import com.chengliuxiang.xiaochengshu.comment.biz.rpc.UserRpcService;
import com.chengliuxiang.xiaochengshu.comment.biz.service.CommentService;
import com.chengliuxiang.xiaochengshu.kv.dto.req.FindCommentContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.rsp.FindCommentContentRspDTO;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Resource
    private SendMqRetryHelper sendMqRetryHelper;
    @Resource
    private DistributedGeneratorRpcService distributedGeneratorRpcService;
    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 评论详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000)
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();


    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        String content = publishCommentReqVO.getContent(); // 评论正文
        // TODO：后续需要支持上传多张图片
        String imageUrl = publishCommentReqVO.getImageUrl(); // 评论图片
        Preconditions.checkArgument(StringUtils.isNotEmpty(content) || StringUtils.isNotEmpty(imageUrl),
                "评论正文和图片不能同时为空");

        Long creatorId = LoginUserContextHolder.getUserId(); // 发布者 ID
        String commentId = distributedGeneratorRpcService.generateCommentId(); // 生成评论 ID
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .commentId(Long.valueOf(commentId))
                .noteId(publishCommentReqVO.getNoteId())
                .content(content)
                .imageUrl(imageUrl)
                .replyCommentId(publishCommentReqVO.getReplyCommentId())
                .createTime(LocalDateTime.now())
                .creatorId(creatorId)
                .build();
        // 异步发送 MQ（包含重试机制）
        sendMqRetryHelper.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT, JsonUtils.toJsonString(publishCommentMqDTO));
        return Response.success();
    }

    /**
     * 评论列表分页查询
     *
     * @param findCommentPageListReqVO
     * @return
     */
    @Override
    public PageResponse<FindCommentItemRspVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO) {
        Long noteId = findCommentPageListReqVO.getNoteId();
        Integer pageNo = findCommentPageListReqVO.getPageNo();
        long pageSize = 10; // 每页展示一级评论数

        // 先从缓存中查询评论总数
        String noteCommentTotalKey = RedisKeyConstants.buildNoteCommentTotalKey(noteId);
        Number commentTotal = (Number) redisTemplate.opsForHash().get(noteCommentTotalKey, RedisKeyConstants.FIELD_COMMENT_TOTAL);
        long count = Objects.isNull(commentTotal) ? 0L : commentTotal.longValue();

        // 若缓存不存在，则查询数据库
        if (Objects.isNull(commentTotal)) {
            Long dbCount = noteCountDOMapper.selectCommentTotalByNoteId(noteId);
            if (Objects.isNull(dbCount)) { // 如果数据库中也不存在，则抛出业务异常
                throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
            }
            count = dbCount;
            threadPoolTaskExecutor.execute(() -> {
                syncNoteCommentTotal2Redis(noteCommentTotalKey, dbCount);
            });
        }
        if (count == 0) { // 不存在评论
            return PageResponse.success(null, pageNo, pageSize);
        }
        List<FindCommentItemRspVO> commentRspVOS = Lists.newArrayList();

        long offset = PageResponse.getOffset(pageNo, pageSize);

        // 评论分页缓存使用 ZSET + STRING 实现
        // 构建评论 ZSET Key，Score 为评论热度，Member 为评论 ID
        String commentZSetKey = RedisKeyConstants.buildCommentListKey(noteId);
        boolean hasKey = redisTemplate.hasKey(commentZSetKey);
        if (!hasKey) { // 若缓存中不存在，异步将热点评论同步到 redis 中（最多同步 500 条）
            threadPoolTaskExecutor.execute(() -> syncHeatComment2Redis(commentZSetKey, noteId));
        }
        if (hasKey && offset < 500) { // 若 ZSET 缓存存在, 并且查询的是前 50 页的评论
            // 使用 ZRevRange 获取某篇笔记下，按热度降序排序的一级评论 ID
            Set<Object> commentIds = redisTemplate.opsForZSet()
                    .reverseRangeByScore(commentZSetKey, -Double.MAX_VALUE, Double.MAX_VALUE, offset, pageSize);
            if (CollUtil.isNotEmpty(commentIds)) {
                List<Object> commentIdList = Lists.newArrayList(commentIds); // Set 转 List

                // 先查询本地缓存
                // 新建一个集合，用于存储本地缓存中不存在的评论 ID
                List<Long> localCacheExpiredCommentIds = Lists.newArrayList();
                List<Long> localCacheKeys = commentIdList.stream().map(commentId -> Long.valueOf(commentId.toString()))
                        .toList();
                Map<Long, String> commentIdAndDetailJsonMap = LOCAL_CACHE.getAll(localCacheKeys, missingKeys -> {
                    // 对于本地缓存中缺失的 key，返回空字符串
                    Map<Long, String> missingData = Maps.newHashMap();
                    missingKeys.forEach(key -> {
                        localCacheExpiredCommentIds.add(key);
                        missingData.put(key, Strings.EMPTY);
                    });
                    return missingData;
                });

                // 若 localCacheExpiredCommentIds 的大小不等于 commentIdList 的大小，说明本地缓存中有数据
                if (CollUtil.size(localCacheExpiredCommentIds) != commentIds.size()) {
                    // 将本地缓存中的评论详情 Json, 转换为实体类，添加到 VO 返参集合中
                    for (String value : commentIdAndDetailJsonMap.values()) {
                        if (StringUtils.isBlank(value)) continue;
                        FindCommentItemRspVO commentRspVO = JsonUtils.parseObject(value, FindCommentItemRspVO.class);
                        commentRspVOS.add(commentRspVO);
                    }
                }
                // 若 localCacheExpiredCommentIds 大小等于 0，说明评论详情数据都在本地缓存中，直接响应返参
                if (CollUtil.size(localCacheExpiredCommentIds) == 0) {
                    return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
                }

                // 构建 MGET 批量查询评论详情的 Key 集合
                List<String> commentIdKeys = localCacheExpiredCommentIds.stream()
                        .map(RedisKeyConstants::buildCommentDetailKey).toList();
                // MGET 批量获取评论数据
                List<Object> commentsJsonList = redisTemplate.opsForValue().multiGet(commentIdKeys);

                // 可能存在部分评论不在缓存中，已经过期被删除，这些评论 ID 需要提取出来，等会查数据库
                List<Long> expiredCommentIds = Lists.newArrayList();
                for (int i = 0; i < Objects.requireNonNull(commentsJsonList).size(); i++) {
                    String commentJson = (String) commentsJsonList.get(i);
                    if (Objects.nonNull(commentJson)) {
                        // 缓存中存在的评论 Json，直接转换为 VO 添加到返参集合中
                        FindCommentItemRspVO commentRspVO = JsonUtils.parseObject(commentJson, FindCommentItemRspVO.class);
                        commentRspVOS.add(commentRspVO);
                    } else {
                        expiredCommentIds.add(Long.valueOf(commentIdList.get(i).toString()));
                    }
                }
                // 对于不存在的一级评论，需要批量从数据库中查询，并添加到 commentRspVOS 中
                if (CollUtil.isNotEmpty(expiredCommentIds)) {
                    List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredCommentIds);
                    getCommentDataAndSync2Redis(commentDOS, noteId, commentRspVOS);
                }
            }
            // 按热度值进行降序排序
            commentRspVOS = commentRspVOS.stream().sorted(Comparator.comparing(FindCommentItemRspVO::getHeat).reversed())
                    .collect(Collectors.toList());
            // 异步将评论详情同步到本地缓存
            syncCommentDetail2LocalCache(commentRspVOS);
            return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
        }
        // 缓存中没有，则查询数据库
        // 查询一级评论
        List<CommentDO> oneLevelCommentDOS = commentDOMapper.selectPageList(noteId, offset, pageSize);
        getCommentDataAndSync2Redis(oneLevelCommentDOS, noteId, commentRspVOS);
        // 异步将评论详情同步到本地缓存
        syncCommentDetail2LocalCache(commentRspVOS);
        return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
    }

    /**
     * 设置用户信息
     *
     * @param userIdAndDTOMap
     * @param userId
     * @param findCommentItemRspVO
     */
    private static void setUserInfo(Map<Long, FindUserByIdRspDTO> userIdAndDTOMap, Long userId, FindCommentItemRspVO findCommentItemRspVO) {
        if (CollUtil.isNotEmpty(userIdAndDTOMap)) {
            FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
            if (Objects.nonNull(findUserByIdRspDTO)) {
                findCommentItemRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                findCommentItemRspVO.setNickname(findUserByIdRspDTO.getNickName());
            }
        }
    }

    /**
     * 设置评论内容
     *
     * @param commentUuidAndContentMap
     * @param commentDO
     * @param findCommentItemRspVO
     */
    private static void setCommentContent(Map<String, String> commentUuidAndContentMap, CommentDO commentDO, FindCommentItemRspVO findCommentItemRspVO) {
        if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
            String contentUuid = commentDO.getContentUuid();
            if (StringUtils.isNotBlank(contentUuid)) {
                findCommentItemRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
            }
        }
    }

    /**
     * 同步笔记评论总数到 Redis 中
     *
     * @param noteCommentTotalKey
     * @param dbCount
     */
    private void syncNoteCommentTotal2Redis(String noteCommentTotalKey, Long dbCount) {
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                operations.opsForHash()
                        .put(noteCommentTotalKey, RedisKeyConstants.FIELD_COMMENT_TOTAL, dbCount);
                long expireTime = 60 * 60 + RandomUtil.randomInt(4 * 60 * 60);
                operations.expire(noteCommentTotalKey, expireTime, TimeUnit.SECONDS);
                return null;
            }
        });
    }

    private void syncHeatComment2Redis(String key, Long noteId) {
        List<CommentDO> commentDOS = commentDOMapper.selectHeatComments(noteId);
        if (CollUtil.isNotEmpty(commentDOS)) {
            // 使用 Redis Pipeline 提升写入性能
            redisTemplate.executePipelined((RedisCallback<Object>) connect -> {
                ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
                for (CommentDO commentDO : commentDOS) {
                    Long commentId = commentDO.getId();
                    Double commentHeat = commentDO.getHeat();
                    zSetOps.add(key, commentId, commentHeat);
                }
                // 设置随机过期时间，单位：秒
                int randomExpiryTime = RandomUtil.randomInt(5 * 60 * 60); // 5小时以内
                redisTemplate.expire(key, randomExpiryTime, TimeUnit.SECONDS);
                return null; // 无返回值
            });
        }
    }

    /**
     * 获取全部评论数据，并将评论详情同步到 Redis 中
     *
     * @param oneLevelCommentDOS
     * @param noteId
     * @param commentRspVOS
     */
    private void getCommentDataAndSync2Redis(List<CommentDO> oneLevelCommentDOS, Long noteId, List<FindCommentItemRspVO> commentRspVOS) {
        // 过滤出所有最早回复的二级评论 ID
        List<Long> twoLevelCommentIds = oneLevelCommentDOS.stream()
                .map(CommentDO::getFirstReplyCommentId)
                .filter(firstReplyCommentId -> firstReplyCommentId != 0)
                .toList();

        // 查询二级评论
        Map<Long, CommentDO> commentIdAndDOMap = null;
        List<CommentDO> twoLevelCommonDOS = null;
        if (CollUtil.isNotEmpty(twoLevelCommentIds)) {
            twoLevelCommonDOS = commentDOMapper.selectTwoLevelCommentByIds(twoLevelCommentIds);

            // 转 Map 集合，方便后续拼装数据
            commentIdAndDOMap = twoLevelCommonDOS.stream()
                    .collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
        }

        // 调用 KV 服务需要的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        // 调用用户服务的入参
        List<Long> userIds = Lists.newArrayList();

        // 将一级评论和二级评论合并到一起
        List<CommentDO> allCommentDOS = Lists.newArrayList();
        CollUtil.addAll(allCommentDOS, oneLevelCommentDOS);
        CollUtil.addAll(allCommentDOS, twoLevelCommonDOS);

        // 循环提取 RPC 调用需要的入参数据
        allCommentDOS.forEach(commentDO -> {
            // 构建调用 KV 服务批量查询评论内容的入参
            boolean isContentEmpty = commentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(commentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(commentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }

            // 构建调用用户服务批量查询用户信息的入参
            userIds.add(commentDO.getUserId());
        });

        // RPC: 调用 KV 服务，批量获取评论内容
        List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);

        // DTO 集合转 Map, 方便后续拼装数据
        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent));
        }

        // RPC: 调用用户服务，批量获取用户信息（头像、昵称等）
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        // DTO 集合转 Map, 方便后续拼装数据
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = null;
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto));
        }

        // DO 转 VO, 组合拼装一二级评论数据
        for (CommentDO commentDO : oneLevelCommentDOS) {
            // 一级评论
            Long userId = commentDO.getUserId();
            FindCommentItemRspVO oneLevelCommentRspVO = FindCommentItemRspVO.builder()
                    .userId(userId)
                    .commentId(commentDO.getId())
                    .imageUrl(commentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(commentDO.getCreateTime()))
                    .likeTotal(commentDO.getLikeTotal())
                    .childCommentTotal(commentDO.getChildCommentTotal())
                    .heat(commentDO.getHeat())
                    .build();

            // 用户信息
            setUserInfo(userIdAndDTOMap, userId, oneLevelCommentRspVO);
            // 笔记内容
            setCommentContent(commentUuidAndContentMap, commentDO, oneLevelCommentRspVO);


            // 二级评论
            Long firstReplyCommentId = commentDO.getFirstReplyCommentId();
            if (CollUtil.isNotEmpty(commentIdAndDOMap)) {
                CommentDO firstReplyCommentDO = commentIdAndDOMap.get(firstReplyCommentId);
                if (Objects.nonNull(firstReplyCommentDO)) {
                    Long firstReplyCommentUserId = firstReplyCommentDO.getUserId();
                    FindCommentItemRspVO firstReplyCommentRspVO = FindCommentItemRspVO.builder()
                            .userId(firstReplyCommentDO.getUserId())
                            .commentId(firstReplyCommentDO.getId())
                            .imageUrl(firstReplyCommentDO.getImageUrl())
                            .createTime(DateUtils.formatRelativeTime(firstReplyCommentDO.getCreateTime()))
                            .likeTotal(firstReplyCommentDO.getLikeTotal())
                            .heat(firstReplyCommentDO.getHeat())
                            .build();

                    // 用户信息
                    setUserInfo(userIdAndDTOMap, firstReplyCommentUserId, firstReplyCommentRspVO);
                    // 笔记内容
                    setCommentContent(commentUuidAndContentMap, firstReplyCommentDO, firstReplyCommentRspVO);
                    oneLevelCommentRspVO.setFirstReplyComment(firstReplyCommentRspVO);
                }
            }
            commentRspVOS.add(oneLevelCommentRspVO);
        }

        // 异步将笔记详情，同步到 Redis 中
        threadPoolTaskExecutor.execute(() -> {
            // 准备批量写入的数据
            Map<String, String> data = Maps.newHashMap();
            commentRspVOS.forEach(commentRspVO -> {
                // 评论 ID
                Long commentId = commentRspVO.getCommentId();
                // 构建 Key
                String key = RedisKeyConstants.buildCommentDetailKey(commentId);
                data.put(key, JsonUtils.toJsonString(commentRspVO));
            });

            // 使用 Redis Pipeline 提升写入性能
            redisTemplate.executePipelined((RedisCallback<?>) (connection) -> {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    // 将 Java 对象序列化为 JSON 字符串
                    String jsonStr = JsonUtils.toJsonString(entry.getValue());

                    // 随机生成过期时间 (5小时以内)
                    int randomExpire = RandomUtil.randomInt(5 * 60 * 60);

                    // 批量写入并设置过期时间
                    connection.setEx(
                            redisTemplate.getStringSerializer().serialize(entry.getKey()),
                            randomExpire,
                            redisTemplate.getStringSerializer().serialize(jsonStr)
                    );
                }
                return null;
            });
        });
    }

    /**
     * 同步评论将详情到本地缓存中
     *
     * @param commentRspVOS
     */
    private void syncCommentDetail2LocalCache(List<FindCommentItemRspVO> commentRspVOS) {
        // 开启一个异步线程
        threadPoolTaskExecutor.execute(() -> {
            // 构建缓存所需的键值
            Map<Long, String> localCacheData = Maps.newHashMap();
            commentRspVOS.forEach(commentRspVO -> {
                Long commentId = commentRspVO.getCommentId();
                localCacheData.put(commentId, JsonUtils.toJsonString(commentRspVO));
            });
            // 批量写入本地缓存
            LOCAL_CACHE.putAll(localCacheData);
        });
    }

    @Override
    public PageResponse<FindChildCommentItemRspVO> findChildCommentPageList(FindChildCommentPageListReqVO findChildCommentPageListReqVO) {
        Long parentCommentId = findChildCommentPageListReqVO.getParentCommentId();
        Integer pageNo = findChildCommentPageListReqVO.getPageNo();
        // 每页展示的二级评论数 (小红书 APP 中是一次查询 6 条)
        long pageSize = 6;
        // TODO:先从缓存中查
        Long count = commentDOMapper.selectChildCommentTotalById(parentCommentId);
        if (Objects.isNull(count) || count == 0) {
            return PageResponse.success(null, pageNo, 0);
        }
        // 分页反参 VO
        List<FindChildCommentItemRspVO> childCommentRspVOS = Lists.newArrayList();
        // 计算分页查询的偏移量 offset (需要 +1，因为最早回复的二级评论已经被展示了)
        long offset = PageResponse.getOffset(pageNo, pageSize) + 1;
        List<CommentDO> childCommentDOS = commentDOMapper.selectChildPageList(parentCommentId, offset, pageSize);

        // 调用 KV 服务需要的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        // 调用用户服务需要的入参
        Set<Long> userIds = Sets.newHashSet();
        // 归属的笔记 ID
        Long noteId = null;
        for (CommentDO childCommentDO : childCommentDOS) {
            noteId = childCommentDO.getNoteId();
            boolean isContentEmpty = childCommentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(childCommentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(childCommentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }
            userIds.add(childCommentDO.getUserId());

            Long parentId = childCommentDO.getParentId();
            Long replyCommentId = childCommentDO.getReplyCommentId();
            // 若当前评论的 replyCommentId 不等于 parentId，则前端需要展示回复的哪个用户，如  “回复 橙留香：”
            if (!Objects.equals(parentId, replyCommentId)) {
                userIds.add(childCommentDO.getReplyUserId());
            }
        }

        // RPC：调用 KV 服务，批量获取评论内容
        List<FindCommentContentRspDTO> findCommentContentRspDTOS = keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);
        // DTO 集合转 Map, 方便后续拼装数据
        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent));
        }

        // RPC: 调用用户服务，批量获取用户信息（头像、昵称等）
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds.stream().toList());
        // DTO 集合转 Map, 方便后续拼装数据
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = null;
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto));
        }

        // DO 转 VO
        for (CommentDO childCommentDO : childCommentDOS) {
            Long userId = childCommentDO.getUserId();
            FindChildCommentItemRspVO childCommentRspVO = FindChildCommentItemRspVO.builder()
                    .userId(userId)
                    .commentId(childCommentDO.getId())
                    .imageUrl(childCommentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(childCommentDO.getCreateTime()))
                    .likeTotal(childCommentDO.getLikeTotal())
                    .build();

            // 填充用户信息（包括评论发布者、回复的用户）
            if (CollUtil.isNotEmpty(userIdAndDTOMap)) {
                FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
                if (Objects.nonNull(findUserByIdRspDTO)) {
                    childCommentRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                    childCommentRspVO.setNickname(findUserByIdRspDTO.getNickName());
                }

                // 回复的用户
                Long replyCommentId = childCommentDO.getReplyCommentId();
                Long parentId = childCommentDO.getParentId();
                if (Objects.nonNull(replyCommentId)
                        && !Objects.equals(replyCommentId, parentId)) {
                    Long replyUserId = childCommentDO.getReplyUserId();
                    FindUserByIdRspDTO replyUser = userIdAndDTOMap.get(replyUserId);
                    childCommentRspVO.setReplyUserId(replyUser.getId());
                    childCommentRspVO.setReplyUserName(replyUser.getNickName());
                }
            }

            // 评论内容
            if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
                String contentUuid = childCommentDO.getContentUuid();
                if (StringUtils.isNotEmpty(contentUuid)) {
                    childCommentRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
                }
            }
            childCommentRspVOS.add(childCommentRspVO);
        }
        return PageResponse.success(childCommentRspVOS, pageNo, count, pageSize);
    }
}
