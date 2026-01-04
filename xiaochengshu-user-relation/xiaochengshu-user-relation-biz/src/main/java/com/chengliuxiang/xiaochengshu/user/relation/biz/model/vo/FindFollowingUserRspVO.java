package com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FindFollowingUserRspVO {
    private Long userId;

    private String nickname;

    private String avatar;

    private String introduction;
}
