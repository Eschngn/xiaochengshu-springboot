package com.chengliuxiang.xiaochengshu.kv.dto.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FindCommentContentRspDTO {

    private String contentId;

    private String content;
}
