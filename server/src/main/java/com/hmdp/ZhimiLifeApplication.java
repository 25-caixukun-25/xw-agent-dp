package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class ZhimiLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhimiLifeApplication.class, args);
    }

}
