package com.chengliuxiang.xiaochengshu.comment.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.constant.DateConstants;
import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.framework.common.util.DateUtils;
import com.chengliuxiang.framework.common.util.JsonUtils;
import com.chengliuxiang.xiaochengshu.comment.biz.constant.MQConstants;
import com.chengliuxiang.xiaochengshu.comment.biz.domain.dataobject.CommentDO;
import com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper.CommentDOMapper;
import com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper.NoteCountDOMapper;
import com.chengliuxiang.xiaochengshu.comment.biz.model.dto.PublishCommentMqDTO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.FindCommentItemRspVO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.FindCommentPageListReqVO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.PublishCommentReqVO;
import com.chengliuxiang.xiaochengshu.comment.biz.retry.SendMqRetryHelper;
import com.chengliuxiang.xiaochengshu.comment.biz.rpc.DistributedGeneratorRpcService;
import com.chengliuxiang.xiaochengshu.comment.biz.rpc.KeyValueRpcService;
import com.chengliuxiang.xiaochengshu.comment.biz.rpc.UserRpcService;
import com.chengliuxiang.xiaochengshu.comment.biz.service.CommentService;
import com.chengliuxiang.xiaochengshu.kv.dto.req.FindCommentContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.rsp.FindCommentContentRspDTO;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        // TODO:先从缓存中查
        Long count = noteCountDOMapper.selectCommentTotalByNoteId(noteId);
        if (Objects.isNull(count)) {
            return PageResponse.success(null, pageNo, pageSize);
        }
        List<FindCommentItemRspVO> commentRspVOS = null;
        if (count > 0) {
            commentRspVOS = Lists.newArrayList();
            long offset = PageResponse.getOffset(pageNo, pageSize);
            List<CommentDO> oneLevelCommentDOS = commentDOMapper.selectPageList(noteId, offset, pageSize);
            List<Long> twoLevelCommentIds = oneLevelCommentDOS.stream()
                    .map(CommentDO::getFirstReplyCommentId)
                    .filter(firstReplyCommentId -> firstReplyCommentId != 0)
                    .toList(); // 过滤出所有最早回复的二级评论 ID

            Map<Long, CommentDO> commentIdAndDOMap = null; // 二级评论的 id-do Map
            List<CommentDO> twoLevelCommonDOS = null;
            if (CollUtil.isNotEmpty(twoLevelCommentIds)) {
                twoLevelCommonDOS = commentDOMapper.selectTwoLevelCommentByIds(twoLevelCommentIds);
                commentIdAndDOMap = twoLevelCommonDOS.stream()
                        .collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
            }
            List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList(); // 调用 KV 服务需要的入参
            List<Long> userIds = Lists.newArrayList(); // 调用用户服务需要的入参
            // 将一级评论和二级评论合并到一起
            List<CommentDO> allCommentDOS = Lists.newArrayList();
            CollUtil.addAll(allCommentDOS, oneLevelCommentDOS);
            CollUtil.addAll(allCommentDOS, twoLevelCommonDOS);
            // 构建入参
            allCommentDOS.forEach(commentDO -> {
                boolean isContentEmpty = commentDO.getIsContentEmpty();
                if (!isContentEmpty) {
                    FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                            .contentId(commentDO.getContentUuid())
                            .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(commentDO.getCreateTime()))
                            .build();
                    findCommentContentReqDTOS.add(findCommentContentReqDTO);
                }
                userIds.add(commentDO.getUserId());
            });

            List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                    keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);
            Map<String, String> commentUuidAndContentMap = null;
            if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
                commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                        .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent));
            }

            List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);
            Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = null;
            if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
                userIdAndDTOMap = findUserByIdRspDTOS.stream()
                        .collect(Collectors.toMap(FindUserByIdRspDTO::getId, findUserByIdRspDTO -> findUserByIdRspDTO));
            }

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
                        .build();
                // 用户信息
                setUserInfo( userIdAndDTOMap, userId, oneLevelCommentRspVO);
                // 笔记内容
                setCommentContent(commentUuidAndContentMap, commentDO, oneLevelCommentRspVO);

                // 二级评论
                Long firstReplyCommentId = commentDO.getFirstReplyCommentId();
                if(CollUtil.isNotEmpty(commentIdAndDOMap)){
                    CommentDO firstReplyCommentDO = commentIdAndDOMap.get(firstReplyCommentId);
                    if(Objects.nonNull(firstReplyCommentDO)){
                        Long firstReplyCommentUserId = firstReplyCommentDO.getUserId();
                        FindCommentItemRspVO firstReplyCommentRspVO = FindCommentItemRspVO.builder()
                                .userId(firstReplyCommentDO.getUserId())
                                .commentId(firstReplyCommentDO.getId())
                                .imageUrl(firstReplyCommentDO.getImageUrl())
                                .createTime(DateUtils.formatRelativeTime(firstReplyCommentDO.getCreateTime()))
                                .likeTotal(firstReplyCommentDO.getLikeTotal())
                                .build();
                        // 用户信息
                        setUserInfo( userIdAndDTOMap, firstReplyCommentUserId, firstReplyCommentRspVO);
                        // 笔记内容
                        setCommentContent(commentUuidAndContentMap, firstReplyCommentDO, firstReplyCommentRspVO);
                        oneLevelCommentRspVO.setFirstReplyComment(firstReplyCommentRspVO);
                    }
                }
                commentRspVOS.add(oneLevelCommentRspVO);


            }


        }
        return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
    }

    /**
     * 设置用户信息
     * @param userIdAndDTOMap
     * @param userId
     * @param findCommentItemRspVO
     */
    private static void setUserInfo( Map<Long, FindUserByIdRspDTO> userIdAndDTOMap, Long userId, FindCommentItemRspVO findCommentItemRspVO) {
        if(CollUtil.isNotEmpty(userIdAndDTOMap)){
            FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
            if (Objects.nonNull(findUserByIdRspDTO)) {
                findCommentItemRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                findCommentItemRspVO.setNickname(findUserByIdRspDTO.getNickName());
            }
        }
    }

    /**
     * 设置评论内容
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
}
