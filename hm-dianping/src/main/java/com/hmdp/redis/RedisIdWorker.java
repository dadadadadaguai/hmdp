package com.hmdp.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局ID生成器
 */
@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        //时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTime = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTime - BEGIN_TIMESTAMP;

        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);
        return timeStamp << COUNT_BITS | count;
    }
}
