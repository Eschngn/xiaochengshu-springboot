package com.chengliuxiang.xiaochengshu.count.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AggregationCountCollectUnCollectNoteMqDTO {
    private Long noteId;

    private Long creatorId;

    private Integer count;
}
