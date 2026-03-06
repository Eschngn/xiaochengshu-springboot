package com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper;

import com.chengliuxiang.xiaochengshu.comment.biz.domain.dataobject.CommentDO;
import com.chengliuxiang.xiaochengshu.comment.biz.model.bo.CommentBO;
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

    int batchInsert(@Param("comments")List<CommentBO>  comments);
}