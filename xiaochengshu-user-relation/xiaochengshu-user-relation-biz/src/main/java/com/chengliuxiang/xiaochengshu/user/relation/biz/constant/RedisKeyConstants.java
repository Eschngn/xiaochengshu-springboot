package com.chengliuxiang.xiaochengshu.user.relation.biz.constant;

public class RedisKeyConstants {
    private static final String USER_FOLLOWING_KEY_PREFIX = "following:";

    public static String buildUserFollowingKey(Long userId) {
        return USER_FOLLOWING_KEY_PREFIX + userId;
    }
}
