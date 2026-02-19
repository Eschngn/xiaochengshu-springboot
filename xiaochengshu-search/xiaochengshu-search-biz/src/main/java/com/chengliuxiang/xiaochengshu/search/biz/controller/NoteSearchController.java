package com.chengliuxiang.xiaochengshu.search.biz.controller;

import com.chengliuxiang.framework.biz.operationlog.aspect.ApiOperationLog;
import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchNoteReqVO;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchNoteRspVO;
import com.chengliuxiang.xiaochengshu.search.biz.service.NoteSearchService;
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
public class NoteSearchController {

    @Resource
    private NoteSearchService noteSearchService;

    @PostMapping("/note")
    @ApiOperationLog(description = "搜索笔记")
    public PageResponse<SearchNoteRspVO> searchNote(@RequestBody @Validated SearchNoteReqVO searchNoteReqVO) {
        return noteSearchService.searchNote(searchNoteReqVO);
    }
}
