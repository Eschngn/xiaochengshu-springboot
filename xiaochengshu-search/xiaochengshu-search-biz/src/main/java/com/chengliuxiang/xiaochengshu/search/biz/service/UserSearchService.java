package com.chengliuxiang.xiaochengshu.search.biz.service;

import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserReqVO;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserRspVO;

public interface UserSearchService {

    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);
}
