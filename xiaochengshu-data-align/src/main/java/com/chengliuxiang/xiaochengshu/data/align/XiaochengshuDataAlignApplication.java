package com.chengliuxiang.xiaochengshu.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.chengliuxiang.xiaochengshu.data.align.domain.mapper")
public class XiaochengshuDataAlignApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaochengshuDataAlignApplication.class, args);
    }
}
