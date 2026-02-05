package com.chengliuxiang.xiaochengshu.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SelectRecordMapper {

    /**
     * 日增量表：关注数计数变更 - 批量查询
     *
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignFollowingCountTampTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                               @Param("batchSize") int batchSize);

    /**
     * t_following 关注表，获取关注总数
     *
     * @param userId
     * @return
     */
    int selectCountFromFollowingTableByUserId(@Param("userId") long userId);

    /**
     * 日增量表：笔记点赞数变更 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignNoteLikeCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                              @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_like 笔记点赞表，获取点赞总数
     * @param noteId
     * @return
     */
    int selectCountFromNoteLikeTableByNoteId(@Param("noteId") long noteId);
}
