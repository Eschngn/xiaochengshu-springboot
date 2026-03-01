package com.chengliuxiang.xiaochengshu.comment.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.chengliuxiang.xiaochengshu.comment.biz.domain.mapper")
public class XiaochengshuCommentBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuCommentBizApplication.class, args);
    }
}
