package com.chengliuxiang.xiaochengshu.search.biz.domain.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface SelectMapper {

    /**
     * 查询笔记文索引文档所需的全字段数据
     * @param noteId
     * @return
     */
    List<Map<String,Object>> selectEsNoteIndexData(@Param("noteId") long noteId);
}
