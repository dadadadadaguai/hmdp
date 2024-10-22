package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private final StringRedisTemplate stringRedisTemplate;
    //开启线程池
    private static final ExecutorService CACHE_EXECUTOR = Executors.newFixedThreadPool(10);

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 根据id查询商铺信息(采用空对象解决缓存穿透)
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //采用互斥锁解决缓存击穿
        Shop shop = queryByIdWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存穿透
     */
    public Shop queryByIdWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //redis只有空值
        if (shopJson != null) {
            return null;
        }
        Shop shop = this.getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);//设置空对象
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 采用互斥锁解决缓存击穿
     */
    public Shop queryByLookup(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //redis只有空值
        if (shopJson != null) {
            return null;
        }
        //缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            if (!getLock(lockKey)) {
                Thread.sleep(50);
                return queryByLookup(id);
            }
            Shop shop = this.getById(id);
            Thread.sleep(200);  //模拟重建延时
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);//设置空对象
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
    }

    public void addRedisCache(Long id, Long expireTimeSeconds) throws InterruptedException {
        Shop shop = this.getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTimeSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 采用逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    public Shop queryByIdWithLogicExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }
        //过期了，需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = getLock(lockKey);
        if (isLock) {
            CACHE_EXECUTOR.submit(() -> {
                try {
                    this.addRedisCache(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        return shop;
    }

    /**
     * 更新店铺信息
     *
     * @param shop
     * @return
     */
    @Transactional
    @Override
    public Result updateShop(Shop shop){
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        this.updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 获取互斥锁
     *
     * @param key
     * @return
     */
    public boolean getLock(String key) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 检查键是否存在
        return BooleanUtil.isTrue(result);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
