CREATE TABLE `t_following`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`           bigint unsigned NOT NULL COMMENT '用户ID',
    `following_user_id` bigint unsigned NOT NULL COMMENT '关注的用户ID',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注表';

ALTER TABLE t_following ADD UNIQUE uk_user_id_following_user_id(user_id, following_user_id);
