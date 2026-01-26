package com.chengliuxiang.xiaochengshu.count.biz.constant;

public class RedisKeyConstants {
    /**
     * 用户维度计数
     */
    private static final String COUNT_USER_KEY_PREFIX = "count:user:";

    public static final String FIELD_FANS_TOTAL = "fansTotal";

    public static final String FIElD_FOLLOWING_TOTAL = "followingTotal";

    /**
     * 笔记维度计数
     */
    public static final String COUNT_NOTE_KEY_PREFIX = "count:note:";

    public static final String FIELD_LIKE_TOTAL = "likeTotal";

    public static final String FIELD_COLLECT_TOTAL = "collectTotal";

    public static String buildCountUserKey(Long userId) {
        return COUNT_USER_KEY_PREFIX + userId;
    }

    public static String buildCountNoteKey(Long noteId) {
        return COUNT_NOTE_KEY_PREFIX + noteId;
    }
}
