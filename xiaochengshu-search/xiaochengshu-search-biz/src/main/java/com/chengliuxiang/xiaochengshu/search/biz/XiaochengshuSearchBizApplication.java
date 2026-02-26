package com.chengliuxiang.xiaochengshu.search.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.chengliuxiang.xiaochengshu.search.biz.domain.mapper")
public class XiaochengshuSearchBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuSearchBizApplication.class, args);
    }
}
