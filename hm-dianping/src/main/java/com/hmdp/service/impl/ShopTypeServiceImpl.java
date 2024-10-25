package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.redis.RedisConstants.CACHE_SHOPTYPE_KEY;
import static com.hmdp.redis.RedisConstants.CACHE_SHOPTYPE_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 查询商铺类型（缓存）
     *
     * @return
     */
    @Override
    public Result queryShopTypeList() {
        List<String> shopTypeRedis = stringRedisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);
        if (shopTypeRedis != null && !shopTypeRedis.isEmpty()) {
            //将json转对象
            List<ShopType> shopType = shopTypeRedis.stream().map(json -> JSONUtil.toBean(json, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopType);
        }
        List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return Result.fail("商铺类型不存在");
        }
        //数据库访问命中,数据写到redis并返回
        for (ShopType shopType : shopTypeList) {
            String shopTypeJson = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOPTYPE_KEY, shopTypeJson);
        }
        //设置过期时间
        stringRedisTemplate.expire(CACHE_SHOPTYPE_KEY, CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTypeList);
    }
}
