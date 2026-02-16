package com.chengliuxiang.xiaochengshu.search.biz.controller;

import com.chengliuxiang.framework.biz.operationlog.aspect.ApiOperationLog;
import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserReqVO;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchUserRspVO;
import com.chengliuxiang.xiaochengshu.search.biz.service.UserSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@Slf4j
public class UserSearchController {

    @Resource
    private UserSearchService userSearchService;

    @PostMapping("/user")
    @ApiOperationLog(description = "搜索用户")
    public PageResponse<SearchUserRspVO> searchUser(@RequestBody @Validated SearchUserReqVO searchUserReqVO) {
        return userSearchService.searchUser(searchUserReqVO);
    }
}
