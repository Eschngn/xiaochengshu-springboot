package com.chengliuxiang.xiaochengshu.user.relation.biz.constant;

public interface MQConstants {
    /**
     * Topic: 关注、取关共用一个 Topic
     */
    String TOPIC_FOLLOW_OR_UNFOLLOW = "FollowUnfollowTopic";

    /**
     * 关注 Tag
     */
    String TAG_FOLLOW = "Follow";

    /**
     * 取关 Tag
     */
    String TAG_UNFOLLOW = "Unfollow";
}
