package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class CacheClient {
    @Resource
    private RedisTemplate redisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(key).toString();
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //判断命的中是空值
        if (json == null) {
            return null;
        }
        //不存在根据id查询数据库
        R r = dbFallback.apply(id);
        //不存在返回错误
        if (r == null) {
            redisTemplate.opsForValue().set(key, "null", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在写入redis
        this.set(key, r, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //
        return r;
    }


    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Long time, TimeUnit unit, Class<R> type, Function<ID, R> dbFallback) {

        String key = keyPrefix + id;
        //1.从redis查商铺缓存
        String rJson = (String) redisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(rJson)) {
            //3.不存在直接返回空
            return null;
        }
        //4.命中,先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(rJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.判断缓存是否过期
        //5.1未过期，直接返回商铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //5.2已过期，需要进行缓存重建
        //6缓存重建

        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        //6.2判断获取锁是否成功
        if (tryLock(lockKey)) {

            //6.3成功开启独立线程进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });


        }
        //6.4返回旧数据
        return r;


    }
    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }
}
