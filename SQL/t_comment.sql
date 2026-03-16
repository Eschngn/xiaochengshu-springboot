CREATE TABLE `t_comment`
(
    `id`                  bigint (20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
    `note_id`             bigint (20) unsigned NOT NULL COMMENT '关联的笔记ID',
    `user_id`             bigint (20) unsigned NOT NULL COMMENT '发布者用户ID',
    `content_uuid`        varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '评论内容UUID',
    `is_content_empty`    bit(1)      NOT NULL                                         DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：为空)',
    `image_url`           varchar(80) NOT NULL                                         DEFAULT '' COMMENT '评论附加图片URL',
    `level`               tinyint (2) NOT NULL DEFAULT '1' COMMENT '级别(1：一级评论 2：二级评论)',
    `reply_total`         bigint (20) unsigned DEFAULT 0 COMMENT '评论被回复次数，仅一级评论需要',
    `child_comment_total` bigint unsigned DEFAULT '0' COMMENT '二级评论总数（只有一级评论才需要统计）',
    `like_total`          bigint (20) DEFAULT 0 COMMENT '评论被点赞次数',
    `parent_id`           bigint (20) unsigned DEFAULT 0 COMMENT '父ID (若是对笔记的评论，则此字段存储笔记ID; 若是二级评论，则此字段存储一级评论的ID)',
    `reply_comment_id`    bigint (20) unsigned DEFAULT 0 COMMENT '回复哪个的评论 (0表示是对笔记的评论，若是对他人评论的回复，则存储回复评论的ID)',
    `reply_user_id`       bigint (20) unsigned DEFAULT 0 COMMENT '回复的哪个用户, 存储用户ID',
    `is_top`              tinyint (2) NOT NULL DEFAULT '0' COMMENT '是否置顶(0：不置顶 1：置顶)',
    `create_time`         datetime    NOT NULL                                         DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         datetime    NOT NULL                                         DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY                   `idx_note_id` (`note_id`) USING BTREE,
    KEY                   `idx_user_id` (`user_id`) USING BTREE,
    KEY                   `idx_parent_id` (`parent_id`) USING BTREE,
    KEY                   `idx_create_time` (`create_time`) USING BTREE,
    KEY                   `idx_reply_comment_id` (`reply_comment_id`) USING BTREE,
    KEY                   `idx_reply_user_id` (`reply_user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '评论表';


-- Cassandra 评论内容表
CREATE TABLE comment_content
(
    note_id    BIGINT, -- 笔记 ID，分区键
    year_month TEXT,   -- 发布年月
    content_id UUID,   -- 评论内容 ID
    content    TEXT,
    PRIMARY KEY ((note_id, year_month),
    content_id
)
    );
