package com.yml.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 超时时间,过期后自动释放
     * @return
     */
    boolean tryLock(long timeoutSec);

    void unlock();
}
