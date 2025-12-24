package com.chengliuxiang.xiaochengshu.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.chengliuxiang.xiaochengshu.user.relation.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.chengliuxiang.xiaochengshu")
public class XiaochengshuUserRelationBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuUserRelationBizApplication.class, args);
    }
}
