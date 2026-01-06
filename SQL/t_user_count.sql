CREATE TABLE `t_user_count`
(
    `id`              bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`         bigint(11) unsigned NOT NULL COMMENT '用户ID',
    `fans_total`      bigint(11) DEFAULT '0' COMMENT '粉丝总数',
    `following_total` bigint(11) DEFAULT '0' COMMENT '关注总数',
    `note_total`      bigint(11) DEFAULT '0' COMMENT '发布笔记总数',
    `like_total`      bigint(11) DEFAULT '0' COMMENT '获得点赞总数',
    `collect_total`   bigint(11) DEFAULT '0' COMMENT '获得收藏总数',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户计数表';
