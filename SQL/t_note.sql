CREATE TABLE `t_note`
(
    `id`               bigint(11) unsigned NOT NULL COMMENT '主键ID',
    `title`            varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
    `is_content_empty` bit(1)                                                       NOT NULL DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：空)',
    `content_uuid`     varchar(36)                                                           DEFAULT '' COMMENT '笔记内容UUID',
    `creator_id`       bigint(11) unsigned NOT NULL COMMENT '发布者ID',
    `topic_id`         bigint(11) unsigned DEFAULT NULL COMMENT '话题ID',
    `topic_name`       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT '' COMMENT '话题名称',
    `is_top`           bit(1)                                                       NOT NULL DEFAULT b'0' COMMENT '是否置顶(0：未置顶 1：置顶)',
    `type`             tinyint(2) DEFAULT '0' COMMENT '类型(0：图文 1：视频)',
    `img_uris`         varchar(660) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '笔记图片链接(逗号隔开)',
    `video_uri`        varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '视频链接',
    `visible`          tinyint(2) DEFAULT '0' COMMENT '可见范围(0：公开,所有人可见 1：仅对自己可见)',
    `create_time`      datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `status`           tinyint(2) NOT NULL DEFAULT '0' COMMENT '状态(0：待审核 1：正常展示 2：被删除(逻辑删除) 3：被下架)',
    PRIMARY KEY (`id`) USING BTREE,
    KEY                `idx_creator_id` (`creator_id`),
    KEY                `idx_topic_id` (`topic_id`),
    KEY                `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记表';


-- Cassandra 笔记内容表
CREATE TABLE note_content
(
    id      UUID PRIMARY KEY,
    content TEXT
);
