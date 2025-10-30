package com.hmdp.utils;

import cn.hutool.core.lang.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * SimpleRedisLock类，实现ILock接口，是一个基于Redis的简单分布式锁实现
 */
public class SimpleRedisLock  implements ILock{

    // StringRedisTemplate对象，用于操作Redis
      private final StringRedisTemplate stringRedisTemplate;  // 使用StringRedisTemplate执行Redis操作
      private final String name; //锁的名称，用于标识不同的锁
      private static final String KEY_PREFIX = "lock:"; // 键的前缀，用于区分不同的锁
      private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"--" ;
      private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
      static {
          UNLOCK_SCRIPT = new DefaultRedisScript<>();
          UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
          UNLOCK_SCRIPT.setResultType(Long.class);
      }
    /**
     * 构造方法
     * @param name 锁的名称
     * @param stringRedisTemplate Redis操作模板
     */
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;  // 初始化锁的名称
        this.stringRedisTemplate = stringRedisTemplate;  // 初始化Redis操作模板
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的过期时间（秒）
     * @return 是否获取锁成功
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //  获取线程标识，确保锁不会被其他线程误删
        String clientId =  ID_PREFIX + Thread.currentThread().getId();
        //  获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, clientId, timeoutSec, TimeUnit.SECONDS);
         // 判断获取锁成功与否
        return Boolean.TRUE.equals(success);
    }


/**
 * 解锁方法
 * 删除Redis中存储的锁标识
 * 使用Lua脚本确保操作的原子性
 *
 */
public void unlock() {

    // 执行Lua脚本进行解锁操作
    // 参数1: UNLOCK_SCRIPT - 解锁的Lua脚本
    // 参数2: Collections.singletonList(KEY_PREFIX + name) - 键的集合，包含锁名称前缀
    // 参数3: ID_PREFIX + Thread.currentThread().getId() - 锁的标识，包含线程ID前缀
    stringRedisTemplate.execute(
            UNLOCK_SCRIPT,
            Collections.singletonList(KEY_PREFIX + name),
            ID_PREFIX + Thread.currentThread().getId()
    );

}
  /*  @Override
    public void unlock() {
        //  获取线程标识，确保锁不会被其他线程误删
        String clientId =  ID_PREFIX + Thread.currentThread().getId();
        //  获取锁的值
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //  判断锁的值是否与线程标识一致
        if (clientId.equals(id))
        // 根据键的前缀和名称拼接成完整的键，然后从Redis中删除该键
         stringRedisTemplate.delete(KEY_PREFIX + name);

    }

   */
}
