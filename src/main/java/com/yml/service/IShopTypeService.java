package com.yml.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yml.dto.Result;
import com.yml.entity.ShopType;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryShopType();
}
