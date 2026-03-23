package com.chengliuxiang.xiaochengshu.comment.biz.service;

import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.FindCommentItemRspVO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.FindCommentPageListReqVO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.PublishCommentReqVO;

public interface CommentService {

    /**
     * 发布评论
     *
     * @param publishCommentReqVO
     * @return
     */
    Response<?> publishComment(PublishCommentReqVO publishCommentReqVO);

    /**
     * 评论列表分页查询
     *
     * @param findCommentPageListReqVO
     * @return
     */
    PageResponse<FindCommentItemRspVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO);
}
