package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@SpringBootTest
class HmDianPingApplicationTests {

    private static final ExecutorService es = Executors.newFixedThreadPool(500);

    @Resource
    private RedisWorker redisWorker;
    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void testShopService() {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    public void testWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisWorker.nextId("order");
                System.out.println("id = " + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - begin) + "ms");
    }

    public void test3(){
        ReentrantLock l = new ReentrantLock();
        l.lock();
    }
}
