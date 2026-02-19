package com.chengliuxiang.xiaochengshu.search.biz.service;

import com.chengliuxiang.framework.common.response.PageResponse;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchNoteReqVO;
import com.chengliuxiang.xiaochengshu.search.biz.model.vo.SearchNoteRspVO;

public interface NoteSearchService {

    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);
}
