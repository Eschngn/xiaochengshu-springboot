package com.chengliuxiang.xiaochengshu.count.biz.constant;

public class RedisKeyConstants {
    private static final String COUNT_USER_KEY_PREFIX = "count:user:";

    public static final String FIELD_FANS_TOTAL = "fansTotal";

    public static String buildCountUserKey(Long userId) {
        return COUNT_USER_KEY_PREFIX + userId;
    }
}
