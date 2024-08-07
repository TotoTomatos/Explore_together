package com.hmdp.entity;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock{


    private RedisTemplate redisTemplate;
    private String name;



    public SimpleRedisLock(RedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"_";
    @Override
    public boolean tryLock(Long timeSec) {
        //获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        String id = (String) redisTemplate.opsForValue().get(KEY_PREFIX + name);

        if(ThreadId.equals(id)){
            redisTemplate.delete(KEY_PREFIX+name);
        }

    }
}
