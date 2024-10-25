package com.hmdp.redis;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisLockImpl implements RedisLock {

    private final String name;  //锁的名字
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PREFIX = "lock:";
    private static final String VALUE_PREFIX = UUID.randomUUID().toString(true) + "-";

    public RedisLockImpl(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean getLock(Long expireTime) {
        String value = VALUE_PREFIX + Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(PREFIX + name, value, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unLock() {
        //解决极限情况下,误删锁的情况。
        String value = VALUE_PREFIX + Thread.currentThread().getId();
        String currenThreadId = stringRedisTemplate.opsForValue().get(PREFIX + name);
        if (value.equals(currenThreadId)) {
            stringRedisTemplate.delete(PREFIX + name);
        }
    }
}
