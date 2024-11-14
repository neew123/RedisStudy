package com.yml.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yml.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REDUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDate.from(LocalDateTime.now().plusSeconds(unit.toSeconds(time))));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R get(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        //1.从redis查询商铺缓存
        String cacheKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //命中空串 ""
        if(json != null){
            return null;
        }
        //未命中 json == null,根据id查询数据库。调用方传递函数逻辑，封装到函数式接口中，执行者执行即可
        R r = dbFallback.apply(id);
        if(r == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(cacheKey, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(cacheKey,r,time,unit);
        return r;
    }


    public <R,ID> R getWithLogicalExpire(String keyPrefix,ID id,Class<R> type,
                                         Function<ID,R> dbFallback,Long time, TimeUnit unit){
        //1.从redis查询商铺缓存
        String cacheKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isBlank(json)) {
            //未命中直接返回NULL
            return null;
        }
        //命中判断逻辑时间是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDate expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(ChronoLocalDate.from(LocalDateTime.now()))){
            return r;
        }
        //过期重建缓存：获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        if (isLock){
            //获取锁成功，缓存重建
            try {
                CACHE_REDUILD_EXECUTOR.submit(()->{
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(cacheKey,r1,time,unit);
                });
            } catch (Exception e){
                throw new RuntimeException(e);
            } finally{
                unLock(lockKey);
            }
        }
        //获取锁失败，返回过期的商铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flagLock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flagLock);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
