package com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper;

import com.chengliuxiang.xiaochengshu.comment.biz.domain.dataobject.CommentDO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.bo.CommentBO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.bo.CommentHeatBO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(CommentDO record);

    int insertSelective(CommentDO record);

    CommentDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentDO record);

    int updateByPrimaryKey(CommentDO record);

    List<CommentDO> selectByCommentIds(@Param("commentIds") List<Long> commentIds);

    int batchInsert(@Param("comments") List<CommentBO> comments);

    int batchUpdateHeatByCommentIds(@Param("commentIds") List<Long> commentIds,
                                    @Param("commentHeatBOS") List<CommentHeatBO> commentHeatBOS);

    /**
     * 查询一级评论下最早回复的评论
     *
     * @param parentId
     * @return
     */
    CommentDO selectEarliestByParentId(Long parentId);

    int updateFirstReplyCommentIdByPrimaryKey(@Param("firstReplyCommentId") Long firstReplyCommentId,
                                              @Param("id") Long id);
}