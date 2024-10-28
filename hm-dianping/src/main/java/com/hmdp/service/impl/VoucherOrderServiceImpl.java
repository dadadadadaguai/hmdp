package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.redis.RedisIdWorker;
import com.hmdp.service.IVoucherOrderService;
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
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final RedisIdWorker redisIdWorker;
    private final SeckillVoucherServiceImpl seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    public VoucherOrderServiceImpl(RedisIdWorker redisIdWorker, SeckillVoucherServiceImpl seckillVoucherService,
                                   StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPTS;
    //创建阻塞队列
    private static final BlockingQueue<VoucherOrder> seckill_Order_queue = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService seckillThread = Executors.newSingleThreadExecutor();
    private static IVoucherOrderService proxy;

    static {
        SECKILL_SCRIPTS = new DefaultRedisScript<>();
        SECKILL_SCRIPTS.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPTS.setResultType(Long.class);
    }

    @PostConstruct
    private void init() {
        seckillThread.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder voucherOrder = seckill_Order_queue.take();
                    handleSeckillOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单处理异常", e);
                }
            }
        }
    }

    /**
     * 优惠券秒杀
     * V2:解决一人一单
     * V3:采用异步线程处理
     *
     * @param voucherId
     * @return Result
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        log.info("用户" + userId + "正在下单" + voucherId);
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPTS, Collections.emptyList(), voucherId.toString(), userId.toString());
        int r = result.intValue();
        if (r != 0) {
            return r == 2 ? Result.fail("请勿重复下单") : Result.fail("库存不足");
        }
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder().setVoucherId(voucherId).setUserId(userId).setId(orderId);
        //添加到阻塞队列中
        seckill_Order_queue.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    private void handleSeckillOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock redissonLock = redissonClient.getLock("voucherOrder:" + userId);
        if (!redissonLock.tryLock()) {
            log.error("请勿重复下单");
        }
        try {
            //获取代理对象(启动类设置暴露代理对象，添加依赖)
            proxy.seckillVoucherByLock(voucherOrder);
        } finally {
            redissonLock.unlock();
        }
    }

    @Transactional
    public void seckillVoucherByLock(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Integer secKillVoucherCount = this.query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (secKillVoucherCount > 0) {
            log.error("已重复购买");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_Id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("扣减库存失败");
        }
        this.save(voucherOrder);
    }
/*    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀活动未开始");
        }
        if (voucher.getEndTime().isBefore(now)) {
            return Result.fail("秒杀活动已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        *//*
          因为toString方法会每次会new一个新对象，导致锁的是同一个id的不同对象，还是没锁同一个id，采用intern()，
          会从字符串常量池里面拿，第一次访问后，会直接从常量池拿，不会new一个新对象，所以不会出现锁不同对象
         *//*
//        RedisLockImpl redisLock = new RedisLockImpl("voucherOrder:" + userId, stringRedisTemplate);
        RLock redissonLock = redissonClient.getLock("voucherOrder:" + userId);
        if (!redissonLock.tryLock()) {
            return Result.fail("请勿重复下单");
        }
        try {
            //获取代理对象(启动类设置暴露代理对象，添加依赖)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.seckillVoucherByLock(voucherId);
        } finally {
            redissonLock.unlock();
        }
    }*/

}
