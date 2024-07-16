package com.hmdp.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {


    @Resource
    private RedisTemplate redisTemplate;

    private static final long BEGIN_TIMESTAMP = 1577836800L;

    public long nextId(String keyPrefix) {

        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC);
        timestamp = timestamp - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接并返回
        return timestamp << 32 | count;
    }
}
