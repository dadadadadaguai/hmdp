package com.hmdp.redis;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisLockImpl implements RedisLock {

    private final String name;  //锁的名字
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PREFIX = "lock:";
    private static final String VALUE_PREFIX = UUID.randomUUID().toString(true) + "-";
    private final static DefaultRedisScript<Long> REDIS_SCRIPT;

    public RedisLockImpl(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    static {
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("redisUnLock.lua"));
        REDIS_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean getLock(Long expireTime) {
        String value = VALUE_PREFIX + Thread.currentThread().getId(); //当前线程标识
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(PREFIX + name, value, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unLock() {
        //解决极限情况下,误删锁的情况。
        stringRedisTemplate
                .execute(REDIS_SCRIPT,
                        Collections.singletonList(PREFIX + name),
                        VALUE_PREFIX + Thread.currentThread()
                                .getId());
    }
}
