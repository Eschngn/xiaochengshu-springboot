package com.chengliuxiang.xiaochengshu.count.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CommentLevelEnum {
    // 一级评论
    ONE(1),
    // 二级评论
    TWO(2),
    ;

    private final Integer code;
}
