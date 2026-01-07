package com.chengliuxiang.xiaochengshu.user.relation.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FollowUnfollowTypeEnum {

    FOLLOW(1),
    UNFOLLOW(0),
    ;

    private final Integer code;
}
