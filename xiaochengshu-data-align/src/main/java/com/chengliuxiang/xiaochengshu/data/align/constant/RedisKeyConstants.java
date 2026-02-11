package com.chengliuxiang.xiaochengshu.data.align.constant;

public class RedisKeyConstants {

    /**
     * 布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞（笔记 ID）前缀
     */
    public static final String BLOOM_TODAY_NOTE_LIKE_LIST_KEY = "bloom:dataAlign:note:like:noteIds:";

    /**
     * 布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞（笔记发布者 ID）前缀
     */
    public static final String BLOOM_TODAY_USER_LIKE_LIST_KEY = "bloom:dataAlign:user:like:userIds";

    /**
     * 布隆过滤器：日增量变更数据，用户笔记收藏，取消收藏（笔记 ID）前缀
     */
    public static final String BLOOM_TODAY_NOTE_COLLECT_LIST_KEY = "bloom:dataAlign:note:collect:noteIds:";

    /**
     * 布隆过滤器：日增量变更数据，用户笔记收藏，取消收藏（笔记发布者 ID）前缀
     */
    public static final String BLOOM_TODAY_USER_COLLECT_LIST_KEY = "bloom:dataAlign:user:collect:userIds";

    /**
     * 布隆过滤器：日增量变更数据，用户笔记发布，删除 前缀
     */
    public static final String BLOOM_TODAY_USER_NOTE_PUBLISH_LIST_KEY = "bloom:dataAlign:user:publish:userIds";

    /**
     * 布隆过滤器：日增量变更数据，用户关注数 前缀
     */
    public static final String BLOOM_TODAY_USER_FOLLOW_LIST_KEY = "bloom:dataAlign:user:follow:userIds";

    /**
     * 布隆过滤器：日增量变更数据，用户粉丝数 前缀
     */
    public static final String BLOOM_TODAY_USER_FANS_LIST_KEY = "bloom:dataAlign:user:fans:userIds";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞（笔记 ID） KEY
     *
     * @param date
     * @return
     */
    public static String buildBloomNoteLikeListKey(String date) {
        return BLOOM_TODAY_NOTE_LIKE_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞（笔记发布者 ID） KEY
     *
     * @param date
     * @return
     */
    public static String buildBloomUserLikeListKey(String date) {
        return BLOOM_TODAY_USER_LIKE_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记收藏，取消收藏（笔记 ID） KEY
     *
     * @param date
     * @return
     */
    public static String buildBloomNoteCollectListKey(String date) {
        return BLOOM_TODAY_NOTE_COLLECT_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记收藏，取消收藏（笔记发布者 ID） KEY
     *
     * @param date
     * @return
     */
    public static String buildBloomUserCollectListKey(String date) {
        return BLOOM_TODAY_USER_COLLECT_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记发布，删除 KEY
     *
     * @param date
     * @return
     */
    public static String buildBloomUserNotePublishListKey(String date) {
        return BLOOM_TODAY_USER_NOTE_PUBLISH_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户关注数 KEY
     *
     * @param date
     * @return
     */
    public static String buildBloomUserFollowListKey(String date) {
        return BLOOM_TODAY_USER_FOLLOW_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户粉丝数 KEY
     *
     * @param date
     * @return
     */
    public static String buildBloomUserFansListKey(String date) {
        return BLOOM_TODAY_USER_FANS_LIST_KEY + date;
    }

    private static final String COUNT_USER_KEY_PREFIX = "count:user:";
    public static final String FIELD_FOLLOWING_TOTAL = "followingTotal";
    public static final String FIELD_FANS_TOTAL = "fansTotal";
    public static final String FIELD_NOTE_TOTAL = "noteTotal";

    public static String buildCountUserKey(Long userId) {
        return COUNT_USER_KEY_PREFIX + userId;
    }

    private static final String COUNT_NOTE_KEY_PREFIX = "count:note:";
    public static String FIELD_LIKE_TOTAL = "likeTotal";
    public static String FIELD_COLLECT_TOTAL = "collectTotal";

    public static String buildCountNoteKey(Long noteId) {
        return COUNT_NOTE_KEY_PREFIX + noteId;
    }
}
