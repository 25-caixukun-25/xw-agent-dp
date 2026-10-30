package com.hmdp.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        if (StrUtil.isNotBlank(password)) {
            serverConfig.setPassword(password);
        }
        return Redisson.create(config);
    }
}
