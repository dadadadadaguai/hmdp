-- 释放锁 ,检查释放时锁是否被其他线程修改
if (redis.call("get", KEYS[1]) == ARGV[1]) then
    return redis.call("del", KEYS[1])
end
return 0
