package com.yml.redisdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yml.entity.Shop;
import com.yml.pojo.User;
import com.yml.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class RedisDemoApplicationTests {

    @Autowired
    private StringRedisTemplate redisTemplate;


    @Resource
    private IShopService shopService;
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

    @Test
    void loadShopData(){
        List<Shop> list = shopService.list();
        //把店铺按照typeID进行分组
        Map<Long, List<Shop>> collect = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //分批写入redis
        for(Map.Entry<Long, List<Shop> > entry:collect.entrySet()){
            //获取类型id
            Long typeId = entry.getKey();
            String shopGeoKey = "shop:geo:" + typeId;
            List<Shop> value = entry.getValue();
            //创建location数组
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //写入redis GEOADD key 经度 纬度 member
            for(Shop shop:value){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            redisTemplate.opsForGeo().add(shopGeoKey,locations);
        }
    }

}
