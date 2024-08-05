package com.hmdp.entity;

public interface ILock {

    boolean tryLock(Long key);

    void unlock();
}
