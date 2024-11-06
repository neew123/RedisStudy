package com.yml.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.entity.Shop;
import com.yml.mapper.ShopMapper;
import com.yml.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.yml.utils.RedisConstants;

import javax.annotation.Resource;


/**
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //1.从redis查询商铺缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        Shop shop = getById(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
