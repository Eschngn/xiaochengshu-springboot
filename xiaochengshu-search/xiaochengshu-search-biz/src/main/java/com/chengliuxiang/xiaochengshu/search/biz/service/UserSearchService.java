package com.chengliuxiang.xiaochengshu.search.biz.service;

import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserReqVO;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserRspVO;
import com.chengliuxiang.xiaochengshu.search.dto.RebuildUserDocumentReqDTO;

public interface UserSearchService {

    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);

    /**
     * 重建用户文档
     * @param rebuildUserDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);
}
