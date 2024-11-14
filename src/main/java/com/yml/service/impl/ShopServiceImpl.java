package com.yml.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.entity.Shop;
import com.yml.mapper.ShopMapper;
import com.yml.service.IShopService;
import com.yml.utils.CacheClient;
import com.yml.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.yml.utils.RedisConstants;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.yml.utils.RedisConstants.CACHE_SHOP_TTL;


/**
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    private static final ExecutorService CACHE_REDUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.get(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        //缓存击穿

        Shop shop1 = cacheClient.getWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        //缓存击穿-互斥锁
        //Shop shop = queryWithMutex(id);

        //缓存击穿-逻辑过期
        //Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //缓存击穿-逻辑过期
    public Shop queryWithLogicalExpire(Long id){
        //1.从redis查询商铺缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isBlank(shopJson)) {
            //未命中直接返回NULL
            return null;
        }
        //命中判断逻辑时间是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDate expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(ChronoLocalDate.from(LocalDateTime.now()))){
            return shop;
        }
        //过期重建缓存：获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        if (isLock){
            //获取锁成功，缓存重建
            try {
                CACHE_REDUILD_EXECUTOR.submit(()->{
                    this.saveShop2Redis(id, 20L);
                });
            } catch (Exception e){
                throw new RuntimeException(e);
            } finally{
                unLock(lockKey);
            }
        }
        //获取锁失败，返回过期的商铺信息
        return shop;
    }

    //缓存穿透
    public Shop queryWithPassThrough(Long id){
        //1.从redis查询商铺缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中空串
        if(shopJson != null){
            return null;
        }
        Shop shop = getById(id);
        if(shop == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(cacheKey, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }


    //缓存击穿-互斥锁
    public Shop queryWithMutex(Long id){
        //1.从redis查询商铺缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //命中空值
        if(shopJson != null){
            return null;
        }
        //既未命中空值，也未命中缓存，实现缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            if(!tryLock(lockKey)){
                //失眠一段时间后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获取锁成功查数据库
            shop = getById(id);
            if(shop == null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(cacheKey, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            unLock(lockKey);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flagLock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flagLock);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long shopId = shop.getId();
        if(shopId == null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shopId);

        return Result.ok();
    }

    public void saveShop2Redis(Long shopId,Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(shopId);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDate.from(LocalDateTime.now().plusSeconds(expireSeconds)));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + shopId,JSONUtil.toJsonStr(redisData));

    }
}
