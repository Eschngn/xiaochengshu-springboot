package com.chengliuxiang.xiaochengshu.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

public interface InsertRecordMapper {

    /**
     * 笔记点赞数：计数变更
     */
    void insert2DataAlignNoteLikeCountTempTable(@Param("tableNameSuffix") String tableNameSuffix, @Param("noteId") Long noteId);

    /**
     * 用户获得的点赞数：计数变更
     */
    void insert2DataAlignUserLikeCountTempTable(@Param("tableNameSuffix") String tableNameSuffix, @Param("userId") Long userId);

    /**
     * 笔记收藏数：计数变更
     */
    void insert2DataAlignNoteCollectCountTempTable(@Param("tableNameSuffix") String tableNamesSuffix, @Param("noteId") Long noteId);

    /**
     * 用户获得的收藏数：计数变更
     */
    void insert2DataAlignUserCollectCountTempTable(@Param("tableNameSuffix") String tableNamesSuffix, @Param("userId") Long userId);
}
