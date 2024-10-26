package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.redis.RedisIdWorker;
import com.hmdp.redis.RedisLockImpl;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final RedisIdWorker redisIdWorker;
    private final SeckillVoucherServiceImpl seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    public VoucherOrderServiceImpl(RedisIdWorker redisIdWorker, SeckillVoucherServiceImpl seckillVoucherService, StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 优惠券秒杀
     * V2:解决一人一单
     *
     * @param voucherId
     * @return Result
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀活动未开始");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        /*
          因为toString方法会每次会new一个新对象，导致锁的是同一个id的不同对象，还是没锁同一个id，采用intern()，
          会从字符串常量池里面拿，第一次访问后，会直接从常量池拿，不会new一个新对象，所以不会出现锁不同对象
         */
        RedisLockImpl redisLock = new RedisLockImpl("voucherOrder:" + userId, stringRedisTemplate);
        if (!redisLock.getLock(1200L)) {
            return Result.fail("请勿重复下单");
        }
        try {
            //获取代理对象(启动类设置暴露代理对象，添加依赖)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.seckillVoucherByLock(voucherId);
        } finally {
            redisLock.unLock();
        }
    }

    @Transactional
    public Result seckillVoucherByLock(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Integer secKillVoucherCount = this.query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (secKillVoucherCount > 0) {
            return Result.fail("已重复购买");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_Id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("扣减库存失败");
        }
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder()
                .setVoucherId(voucherId).setUserId(userId).setId(orderId);
        save(voucherOrder);
        return Result.ok(voucherOrder);
    }
}
