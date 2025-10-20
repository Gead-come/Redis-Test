package com.hmdp.utils;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 缓存客户端组件
 * 使用Redis作为缓存实现，提供基本的缓存操作功能
 */
@Slf4j
@Component
public class CacheClient {
    // 注入StringRedisTemplate，用于Redis操作
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造函数，通过依赖注入StringRedisTemplate
     * @param stringRedisTemplate Redis操作模板
     */
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    // 获取缓存中的数据
    /**
     * 设置缓存
     * @param key 缓存键
     * @param value 缓存值
     * @param time 缓存时间
     * @param unit 时间单位
     */
        // 将value对象转换为JSON字符串后存入Redis，并设置过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithlogicaExpire(String key, Object value, Long time, TimeUnit unit) {
    // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 将RedisData对象转换为JSON字符串后存入Redis，并设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
/**
 * 带有缓存穿透保护的查询方法
 * @param <R> 返回的数据类型
 * @param <ID> 查询条件的ID类型
 * @param keyPrefix Redis中键的前缀
 * @param id 查询条件的ID
 * @param type 返回数据类型的Class对象
 * @param dbFallback 数据库查询函数式接口
 * @param time 缓存过期时间
 * @param unit 时间单位
 * @return 查询结果
 */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R>type,
            Function<ID,R>dbFallback,Long time, TimeUnit unit){
        // 防止缓存穿透：先从redis中查询商铺缓存
        //先从redis中查询商铺缓存
        String key = keyPrefix+ id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(Json)) {
            //存在，直接返回
            return JSONUtil.toBean(Json, type);
        }
        // 判断是否命中的是控制
        if (Json != null) {
            //返回错误信息
            return null;
        }

        //不存在，查询数据库
       R r = dbFallback.apply(id);
        //判断是否存在
        if (r== null) {
            //不存在，返回错误信息
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //存在，写入redis
       set(key,r,time,unit);
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_POOL =
            Executors.newFixedThreadPool(10);

    /**
     * 带有逻辑过期时间的查询方法
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     */
    public <R> R queryWithLogicalExpire(String keyPrefix, Long id, Class<R> type,
              Function<Long, R> dbFallback, Long time, TimeUnit unit) {
        // 防止缓存穿透
        //先从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //存在，直接返回
            return null ;
        }
        //4. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回店铺信息
            return r;
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
                    // 查询数据库
                    R apply = dbFallback.apply(id);
                    // 写入redis
                    setWithlogicaExpire(key, apply, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        //7. 返回店铺信息
        return r;
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

}

