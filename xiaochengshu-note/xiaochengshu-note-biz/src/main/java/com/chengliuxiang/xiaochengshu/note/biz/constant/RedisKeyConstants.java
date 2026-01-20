package com.chengliuxiang.xiaochengshu.note.biz.constant;

public class RedisKeyConstants {
    public static final String NOTE_DETAIL_KEY = "note:detail:";

    public static final String BLOOM_USER_NOTE_LIKE_LIST_KEY = "bloom:note:likes:";

    public static final String USER_NOTE_LIKE_ZSET_KEY = "user:note:likes:";

    public static final String BLOOM_USER_NOTE_COLLECT_LIST_KEY = "bloom:note:collects:";

    public static String buildNoteDetailKey(Long noteId) {
        return NOTE_DETAIL_KEY + noteId;
    }

    public static String buildBloomUserNoteLikeListKey(Long userId) {
        return BLOOM_USER_NOTE_LIKE_LIST_KEY + userId;
    }

    public static String buildUserNoteLikeZSetKey(Long userId) {
        return USER_NOTE_LIKE_ZSET_KEY + userId;
    }

    public static String buildBloomUserNoteCollectListKey(Long userId) {
        return BLOOM_USER_NOTE_COLLECT_LIST_KEY + userId;
    }
}
