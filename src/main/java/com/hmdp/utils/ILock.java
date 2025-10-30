package com.hmdp.utils;

/**
 * 锁接口定义
 * 该接口定义了锁的基本操作方法，包括获取锁和释放锁
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 获取锁的超时时间，单位为秒
     * @return 如果成功获取锁返回true，否则返回false
     */
    boolean tryLock(long timeoutSec);
    /**
     * 释放锁
     * 当使用完锁后，调用此方法释放锁资源
     */
    void unlock();
}
