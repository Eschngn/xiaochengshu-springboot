package com.chengliuxiang.xiaochengshu.note.biz.service;

import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.note.biz.model.vo.*;

public interface NoteService {

    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO);

    Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);

    Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);

    void deleteNoteLocalCache(Long noteId);

    Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO);

    Response<?> topNote(TopNoteReqVO topNoteReqVO);

    Response<?> likeNote(LikeNoteReqVO likeNoteReqVO);

}
