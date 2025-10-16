package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

      @Resource
      private  StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getByIdWithDetails(Long id) {
        // 缓存穿透
        //   Shop shop = queryWithPassThrough(id);
        //  互斥锁解决缓存击穿
       //        Shop shop = queryWithMetex(id);

        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);

    }
    // 创建线程池
    private static final ExecutorService CACHE_REBUILD_POOL =
            Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id){
        // 防止缓存穿透
        //先从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //存在，直接返回
            return null ;
        }
        //4. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回店铺信息
            return shop;
        }
        //5.2 已过期，需要缓存重建
        //6. 缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY  + id;
        boolean tryLock = tryLock(lockKey);
        //6.2 判断是否获取锁成功
        if (tryLock) {
            //6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_POOL.submit(() -> {
                try {
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                  }
                });
            }
        //7. 返回店铺信息
        return shop;
    }

      // 缓存击穿的互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }
    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
    public Shop queryWithMetex(Long id){
        // 防止缓存穿透
        // 1.先从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson) && !"null".equals(shopJson)) {
            // 缓存命中并且不是"null"
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否命中的是控制
        if (shopJson != null) {
            //返回错误信息
            return null;
        }
        //4.实现缓存重建
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY  + id;
        Shop byId = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2判断是否获取锁成功
            if (!isLock) {
                //4.3失败,休眠重试
                Thread.sleep(50);
                // 递归调用
                return queryWithMetex(id);
            }

            //4.4成功，获取锁成功，查询数据库
            byId = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //判断是否存在
            if (byId == null) {
                //不存在，返回错误信息
                stringRedisTemplate.opsForValue().set(key, "null", CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key,
                    JSONUtil.toJsonStr(byId),
                    CACHE_SHOP_TTL,
                    TimeUnit.MINUTES
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(lockKey);
        }

        return byId;

    }
    public Shop queryWithPassThrough(Long id){
        // 防止缓存穿透
        //先从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否命中的是控制
        if (shopJson != null) {
            //返回错误信息
            return null;
        }

        //不存在，查询数据库
        Shop byId = getById(id);
        //判断是否存在
        if (byId == null) {
            //不存在，返回错误信息
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //存在，写入redis
        stringRedisTemplate.opsForValue().set(key,
                JSONUtil.toJsonStr(byId),
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );

        return byId;

}
public void saveShop2Redis(Long id, Long expireTime) throws InterruptedException {
     // 1.查询店铺数据
        Shop shop = getById(id);
        //模拟延迟
    Thread.sleep(200);
     // 2.封装店铺数据
    RedisData redisData = new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime)); //当前时间加多少秒
    // 3.写入redis
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));

}

    @Override    //重写父类方法注解
    @Transactional  //事务管理注解，确保方法执行的事务性
    public Result update(Shop shop) {  //更新店铺信息的方法
        Long id = shop.getId();  //获取店铺ID
        if (id == null) {  //判断店铺ID是否为空
            return Result.fail("店铺id不能为空");  //如果为空，返回失败结果
        }
        //更新数据库  //更新店铺信息到数据库
        updateById(shop);  //调用更新方法
        //删除缓存  //删除对应的缓存数据
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);  //根据ID删除缓存
        return Result.ok();  //返回成功结果

    }
}
