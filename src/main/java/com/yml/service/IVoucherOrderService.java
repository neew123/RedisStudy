package com.yml.service;

import com.yml.dto.Result;

public interface IVoucherOrderService {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
