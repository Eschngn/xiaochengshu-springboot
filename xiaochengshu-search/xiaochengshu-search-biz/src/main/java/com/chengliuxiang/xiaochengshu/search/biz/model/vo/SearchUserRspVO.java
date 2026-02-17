package com.chengliuxiang.xiaochengshu.search.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@NotBlank
@Builder
public class SearchUserRspVO {
    private Long userId;

    private String nickname;

    private String avatar;

    private String xiaochengshuId;

    private Integer noteTotal;

    private String fansTotal;

    /**
     * 昵称关键词高亮
     */
    private String highlightNickname;
}
