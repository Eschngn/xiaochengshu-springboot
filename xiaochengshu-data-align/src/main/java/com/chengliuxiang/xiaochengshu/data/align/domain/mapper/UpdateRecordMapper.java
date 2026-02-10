package com.chengliuxiang.xiaochengshu.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

public interface UpdateRecordMapper {

    int updateUserFollowingTotalByUserId(@Param("userId") long userId,
                                         @Param("followingTotal") int followingTotal);

    int updateNoteLikeTotalByNoteId(@Param("noteId") long noteId,
                                    @Param("noteLikeTotal") int noteLikeTotal);

    int updateNoteCollectTotalByNoteId(@Param("noteId") long noteId,
                                       @Param("noteCollectTotal") int noteCollectTotal);

    int updateUserFansTotalByUserId(@Param("userId") long userId,
                                    @Param("fansTotal") int fansTotal);

    int updateUserLikeTotalByUserId(@Param("userId") long userId,
                                    @Param("userLikeTotal") int userLikeTotal);
}
