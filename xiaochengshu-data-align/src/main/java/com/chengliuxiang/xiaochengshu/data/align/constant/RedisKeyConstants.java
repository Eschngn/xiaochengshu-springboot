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
    public static final String BLOOM_TODAY_NOTE_COLLECT_LIST_KEY="bloom:dataAlign:note:collect:noteIds:";

    /**
     * 布隆过滤器：日增量变更数据，用户笔记收藏，取消收藏（笔记发布者 ID）前缀
     */
    public static final String BLOOM_TODAY_USER_COLLECT_LIST_KEY = "bloom:dataAlign:user:collect:userIds";

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
}
