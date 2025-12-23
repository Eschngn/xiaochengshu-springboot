package com.chengliuxiang.xiaochengshu.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.chengliuxiang.xiaochengshu.user.relation.biz.mapper")
public class XiaochengshuUserRelationBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuUserRelationBizApplication.class, args);
    }
}
