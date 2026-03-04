package com.chengliuxiang.xiaochengshu.comment.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@MapperScan("com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper")
@EnableRetry // 启动 Spring Retry
public class XiaochengshuCommentBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuCommentBizApplication.class, args);
    }
}
