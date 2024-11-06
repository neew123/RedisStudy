package com.yml.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.entity.ShopType;
import com.yml.mapper.ShopTypeMapper;
import com.yml.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import com.yml.utils.RedisConstants;

/**
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopType() {

        List<String> shopJsons = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOPTYPE_KEY, 0, -1);
        if (CollectionUtil.isNotEmpty(shopJsons)) {
            List<ShopType> shopLst =shopJsons.stream().map(json->JSONUtil.toBean(json,ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopLst);
        }
        List<ShopType> shopLst = query().orderByAsc("sort").list();
        List<String> shopJsonsValue = shopLst.stream().map(shopType -> JSONUtil.toJsonStr(shopType)).collect(Collectors.toList());
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOPTYPE_KEY, shopJsonsValue);
        if(CollectionUtil.isEmpty(shopLst)){
            return Result.fail("还未创建商铺类型");
        }
        return Result.ok(shopLst);
    }
}
