package com.chengliuxiang.xiaochengshu.count.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.chengliuxiang.xiaochengshu.count.biz.domain.mapper")
public class XiaochengshuCountBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuCountBizApplication.class, args);
    }
}
