package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.redis.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
@Slf4j
class ShopTypeServiceImplTest {
    @Autowired
    private IShopTypeService shopTypeService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopServiceImpl shopService;

    @Test
    void queryShopTypeList() {
        Result result = shopTypeService.queryShopTypeList();
        log.info(result.getData().toString());
    }

    //添加Redis经纬度数据
    @Test
    void addGeoData() {
        List<Shop> shopList = shopService.list();
        Map<Long, List<Shop>> shopMap = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        shopMap.forEach((typeId, list) -> {
            String key = SHOP_GEO_KEY + typeId;
            for (Shop shop : list) {
                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            }
        });
    }
}