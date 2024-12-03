package com.yml.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.entity.SeckillVoucher;
import com.yml.entity.VoucherOrder;
import com.yml.mapper.VoucherOrderMapper;
import com.yml.service.ISeckillVoucherService;
import com.yml.service.IVoucherOrderService;
import com.yml.utils.RedisIdWorker;
import com.yml.utils.SimpleRedisLock;
import com.yml.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

@Slf4j
@Service
public class IVoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常");
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
       seckillVoucherService.update().
                setSql("stock = stock-1").
                eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).
                update();
        save(voucherOrder);
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation( new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId)  {
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
        //方法一：单机情况下的锁
        //先提交事务再解锁：避免锁已经释放事务还未提交时，其它线程访问不是最新的数据
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //获取事务方法的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            //return proxy.createVoucherOrder(voucherId);
        }

        //方法二：分布式情况下的锁
        SimpleRedisLock orderLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = orderLock.tryLock(5);
        if(!isLock){
            //return Result.fail("不允许重复下单!");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            //return proxy.createVoucherOrder(voucherId);
        }finally {
            orderLock.unlock();
        }

        //方法三：使用Redisson工具提供的锁
        RLock redLock = redissonClient.getLock("order:" + userId);
        boolean redLockRes = false;
        try {
            //三个参数：获取锁的最大等待时间（期间会重试）、锁自动释放时间、时间单位
            redLockRes = redLock.tryLock(1,10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(!redLockRes){
            return Result.fail("不允许重复下单!");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
           // return proxy.createVoucherOrder(voucherId);
        }finally {
            redLock.unlock();
        }

        //方法四：秒杀业务优化，redis记录库存和订单信息，异步同步数据到数据库
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        if(result.intValue() != 0){
            switch (result.intValue()) {
                case 1:
                    return Result.fail("库存不足!");
                case 2:
                    return Result.fail("不能重复下单!");
            }
        }
        // 保存阻塞队列
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);
        return Result.ok(orderId);
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
