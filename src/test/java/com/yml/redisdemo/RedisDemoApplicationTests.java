package com.yml.redisdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yml.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RedisDemoApplicationTests {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Test
    void testSaveUser() throws JsonProcessingException {
        User user = new User("yml", 18);
        //手动序列化
        String json = objectMapper.writeValueAsString(user);
        redisTemplate.opsForValue().set("user:1", json);
        String usrStr = redisTemplate.opsForValue().get("user:1");
        User user1 = objectMapper.readValue(usrStr, User.class);
        System.out.println(user1);

    }

    @Test
    void contextLoads() {
    }

}
