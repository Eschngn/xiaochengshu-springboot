package com.chengliuxiang.xiaochengshu.count.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteLikeDO {
    private Long id;

    private Long userId;

    private Long noteId;

    private Date createTime;

    private Byte status;
}