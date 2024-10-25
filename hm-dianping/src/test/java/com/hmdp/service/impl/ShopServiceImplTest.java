package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.redis.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
@Slf4j
class ShopServiceImplTest {

    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    void queryById() {
        Long id = 1L;
        Result result = shopService.queryById(id);
        log.info("result:{}", result);
    }

    @Test
    void queryByIdWithPassThrough() throws InterruptedException {
        Long id = 1L;
        Long expireTimeSeconds = 10L;
        shopService.addRedisCache(id, expireTimeSeconds);
    }

    @Test
    void deleteLockKey() {
        shopService.unLock("lock:shop:1");
    }
    private ExecutorService es = Executors.newFixedThreadPool(500);  //线程池
    @Test
    void testNextId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                log.debug("id:{}", id);
            }
            latch.countDown();
        };
        long current = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        log.debug("时长为:{}",end-current);
    }
}