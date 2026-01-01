CREATE TABLE `t_fans`
(
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      bigint unsigned NOT NULL COMMENT '用户ID',
    `fans_user_id` bigint unsigned NOT NULL COMMENT '粉丝的用户ID',
    `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户粉丝表';

ALTER TABLE t_fans ADD UNIQUE uk_user_id_fans_user_id(user_id, fans_user_id);
