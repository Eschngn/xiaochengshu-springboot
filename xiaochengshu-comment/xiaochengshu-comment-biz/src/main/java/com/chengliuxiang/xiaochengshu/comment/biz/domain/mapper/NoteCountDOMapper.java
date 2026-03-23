package com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper;

import com.chengliuxiang.xiaochengshu.comment.biz.domain.dataobject.NoteCountDO;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCountDO record);

    int insertSelective(NoteCountDO record);

    NoteCountDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);

    Long selectCommentTotalByNoteId(Long noteId);
}