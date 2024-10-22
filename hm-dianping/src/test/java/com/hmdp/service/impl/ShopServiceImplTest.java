package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
class ShopServiceImplTest {

    @Autowired
    private ShopServiceImpl shopService;

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
}