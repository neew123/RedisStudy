package com.yml.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yml.dto.Result;
import com.yml.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopService extends IService<Shop> {


    Result queryById(Long id);
}
