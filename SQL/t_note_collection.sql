CREATE TABLE `t_note_collection`
(
    `id`          bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     bigint(11) NOT NULL COMMENT '用户ID',
    `note_id`     bigint(11) NOT NULL COMMENT '笔记ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `status`      tinyint(2) NOT NULL DEFAULT '0' COMMENT '收藏状态(0：取消收藏 1：收藏)',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_user_id_note_id` (`user_id`,`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记收藏表';
