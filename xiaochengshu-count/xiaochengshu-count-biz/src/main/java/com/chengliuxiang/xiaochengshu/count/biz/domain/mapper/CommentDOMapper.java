package com.chengliuxiang.xiaochengshu.count.biz.domain.mapper;

import com.chengliuxiang.xiaochengshu.count.biz.domain.dataobject.CommentDO;
import org.apache.ibatis.annotations.Param;

public interface CommentDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(CommentDO record);

    int insertSelective(CommentDO record);

    CommentDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentDO record);

    int updateByPrimaryKey(CommentDO record);

    int updateChildCommentTotal(@Param("parentId") Long parentId, @Param("count") int count);
}