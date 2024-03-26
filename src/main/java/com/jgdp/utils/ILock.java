package com.jgdp.utils;

/**
 * @author 喜欢悠然独自在
 * @version 1.0
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的超时时间，过期自动释放锁
     * @return true表示获取锁成功，false表示获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
