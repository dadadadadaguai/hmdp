package com.hmdp.redis;

public interface RedisLock {

    public boolean getLock(Long expireTime);

    public void unLock();
}
