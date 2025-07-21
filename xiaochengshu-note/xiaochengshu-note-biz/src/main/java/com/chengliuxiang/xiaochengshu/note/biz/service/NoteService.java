package com.chengliuxiang.xiaochengshu.note.biz.service;

import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.note.biz.model.vo.*;

public interface NoteService {

    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO);

    public Response<FindNoteDetailRspVO>  findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);

    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);
}
