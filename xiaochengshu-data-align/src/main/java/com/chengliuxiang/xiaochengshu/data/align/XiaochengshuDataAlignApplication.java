package com.chengliuxiang.xiaochengshu.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.chengliuxiang.xiaochengshu.data.align.domain.mapper")
@EnableFeignClients(basePackages = "com.chengliuxiang.xiaochengshu")
public class XiaochengshuDataAlignApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuDataAlignApplication.class, args);
    }
}
