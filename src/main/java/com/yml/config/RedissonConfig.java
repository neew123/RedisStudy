package com.yml.config;

import io.lettuce.core.RedisClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedisClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("localhost:6379").setPassword("");
        return RedisClient.create();
    }
}
