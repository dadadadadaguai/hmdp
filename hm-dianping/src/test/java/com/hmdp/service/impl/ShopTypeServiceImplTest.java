package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
class ShopTypeServiceImplTest {
    @Autowired
    private IShopTypeService shopTypeService;

    @Test
    void queryShopTypeList() {
        Result result = shopTypeService.queryShopTypeList();
        log.info(result.getData().toString());
    }
}