CREATE TABLE `t_note_count`
(
    `id`            bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id`       bigint(11) unsigned NOT NULL COMMENT '笔记ID',
    `like_total`    bigint(11) DEFAULT '0' COMMENT '获得点赞总数',
    `collect_total` bigint(11) DEFAULT '0' COMMENT '获得收藏总数',
    `comment_total` bigint(11) DEFAULT '0' COMMENT '被评论总数',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记计数表';
