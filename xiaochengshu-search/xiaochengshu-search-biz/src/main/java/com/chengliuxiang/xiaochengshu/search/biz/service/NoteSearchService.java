package com.chengliuxiang.xiaochengshu.search.biz.service;

import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchNoteReqVO;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchNoteRspVO;
import com.chengliuxiang.xiaochengshu.search.dto.RebuildNoteDocumentReqDTO;

public interface NoteSearchService {

    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);

    /**
     * 重建笔记文档
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);
}
