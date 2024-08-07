package com.hmdp.entity;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
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
    private static final DefaultRedisScript<Boolean> UNLOCK_SCRIPT;

    static {
        String format = String.format("if (redis.call('get', KEYS[1]) == ARGV[1]) then\n    redis.call('del', KEYS[1])\n    return 1\nend\nreturn 0");
        UNLOCK_SCRIPT = new DefaultRedisScript<>(format,Boolean.class);
//        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    }
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
        redisTemplate.execute(UNLOCK_SCRIPT,
                Arrays.asList(KEY_PREFIX+name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
