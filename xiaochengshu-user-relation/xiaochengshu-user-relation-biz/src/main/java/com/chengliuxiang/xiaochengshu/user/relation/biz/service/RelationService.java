package com.chengliuxiang.xiaochengshu.user.relation.biz.service;

import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FindFollowingListReqVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FindFollowingUserRspVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FollowUserReqVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.UnfollowUserReqVO;

public interface RelationService {
    /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    Response<?> follow(FollowUserReqVO followUserReqVO);

    /**
     * 取关用户
     * @param unfollowUserReqVO
     * @return
     */
    Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO);

    /**
     * 查询关注列表
     * @param findFollowingListReqVO
     * @return
     */
    PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO);
}
