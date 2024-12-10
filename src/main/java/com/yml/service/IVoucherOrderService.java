package com.yml.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yml.dto.Result;
import com.yml.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
