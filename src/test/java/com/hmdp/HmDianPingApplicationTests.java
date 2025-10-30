package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisWorker redisWorker;
    //x线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);

/**
 * 测试方法：测试将店铺信息保存到Redis的功能
 * @throws InterruptedException 可能在线程等待时被中断
 */
    @Test
    void testSaveShop2Redis() throws InterruptedException { // 测试将店铺信息保存到Redis的方法，接收店铺ID和缓存时长参数
        shopService.saveShop2Redis(1L, 10L); // 调用服务层方法，将ID为1的店铺信息缓存到Redis中，缓存时长为10秒
    }
/**
 * 测试方法：用于将店铺信息保存到缓存中
 * 通过店铺ID获取店铺信息，并将其存入缓存，设置缓存过期时间为10秒
 * @Test 注解表明这是一个测试方法
 */
    @Test
    void SaveShop(){ // 方法名：保存店铺信息到缓存
        Shop shop = shopService.getById(1L); // 根据ID获取店铺信息
        cacheClient.set(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS); // 将店铺信息存入缓存，并设置过期时间为10秒
    }
/**
 * 测试restWorker方法，用于验证Redis工作节点的ID生成性能
 * 使用多线程并发生成300个任务，每个任务生成100个ID
 * 通过CountDownLatch确保所有任务完成后再计算总耗时
 * @throws InterruptedException 如果线程被中断
 */
    @Test
    void restWorker() throws InterruptedException {
        // 创建一个倒计数闩锁，初始值为300，用于等待所有任务完成
        CountDownLatch latch = new CountDownLatch(300);
        // 定义任务逻辑，每个任务循环生成100个ID并打印
        Runnable task = ()->{
           for (int i = 0; i <100 ; i++) {
               // 调用redisWorker生成ID并打印
               Long id = redisWorker.nextId("order");
               System.out.println( "id = " +id );
           }
           // 任务完成后，倒计数减1
           latch.countDown();
       };
        // 记录开始时间
        long start = System.currentTimeMillis();
       // 提交300个任务到线程池执行
       for (int i = 0; i < 300; i++) {
           es.submit(task);
       }
       // 等待所有任务完成
       latch.await();
       // 记录结束时间并计算总耗时
        long end = System.currentTimeMillis();
        System.out.println("time = "+(end-start));
    }


}
