package com.chengliuxiang.xiaochengshu.comment.biz.service;

import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.comment.biz.model.vo.PublishCommentReqVO;

public interface CommentService {

    /**
     * 发布评论
     * @param publishCommentReqVO
     * @return
     */
    Response<?> publishComment(PublishCommentReqVO publishCommentReqVO);
}
