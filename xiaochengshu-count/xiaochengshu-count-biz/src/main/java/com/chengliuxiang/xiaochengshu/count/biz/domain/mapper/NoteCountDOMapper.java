package com.chengliuxiang.xiaochengshu.count.biz.domain.mapper;

import com.chengliuxiang.xiaochengshu.count.biz.domain.dataobject.NoteCountDO;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCountDO record);

    int insertSelective(NoteCountDO record);

    NoteCountDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);
}