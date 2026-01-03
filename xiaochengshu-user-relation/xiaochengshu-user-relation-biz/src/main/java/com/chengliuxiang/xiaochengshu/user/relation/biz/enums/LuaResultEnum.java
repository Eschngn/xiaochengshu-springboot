package com.chengliuxiang.xiaochengshu.user.relation.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LuaResultEnum {
    ZSET_NOT_EXIST(-1L),
    FOLLOW_LIMIT(-2L),
    ALREADY_FOLLOWED(-3L),
    FOLLOW_SUCCESS(0L),
    NOT_FOLLOWED(-4L)
    ;

    private final Long code;

    /**
     * 根据 code 获取对应的枚举类型
     * @param code
     * @return
     */
    public static LuaResultEnum valueOf(Long code) {
        for (LuaResultEnum value : LuaResultEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
