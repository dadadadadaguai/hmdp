package com.hmdp.redis;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisLockImpl implements RedisLock {

    private final String name;  //业务名
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEYPREFIX = "lock:";

    public RedisLockImpl(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean getLock(Long expireTime) {
        long threadId = Thread.currentThread().getId();
        Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEYPREFIX + name, threadId + "", expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unLock() {
        stringRedisTemplate.delete(KEYPREFIX + name);

    }
}
