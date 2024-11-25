package com.yml.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.entity.SeckillVoucher;
import com.yml.entity.VoucherOrder;
import com.yml.mapper.VoucherOrderMapper;
import com.yml.service.ISeckillVoucherService;
import com.yml.service.IVoucherOrderService;
import com.yml.utils.RedisIdWorker;
import com.yml.utils.UserHolder;
import lombok.Synchronized;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class IVoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //判断时间+库存
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束!");
        }
        if (seckillVoucher.getStock() <= 0) {
            return Result.fail("库存不足!");
        }
        //先提交事务再解锁：避免锁已经释放事务还未提交时，其它线程访问不是最新的数据
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //获取事务方法的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //判断是否是一人一单：一人只能领一次
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过一次!");
        }
        //扣减库存  CAS-乐观锁实现  where id = ? and stock > 0
        boolean success = seckillVoucherService.update().
                setSql("stock = stock-1").
                eq("voucher_id", voucherId).gt("stock", 0).
                update();
        if (!success) {
            return Result.fail("库存不足!");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
