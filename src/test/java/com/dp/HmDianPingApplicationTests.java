package com.dp;

import com.dp.entity.Shop;
import com.dp.service.impl.ShopServiceImpl;
import com.dp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void test(){
        Shop shop = shopService.getById(1);
        cacheClient.setWithLogicalExpire("cache:shop:" + 1,shop,1L, TimeUnit.SECONDS);
    }

}
