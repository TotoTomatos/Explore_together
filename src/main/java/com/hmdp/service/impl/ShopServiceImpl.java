package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private RedisTemplate redisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        // Result result = queryWithPassThrough(id);

        //解决缓存穿透+缓存击穿
//        Result result = queryWithMutex(id);

        //使用逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        return Result.ok(shop);
    }

    /**
     * 使用逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis查商铺缓存
        String shopJson = (String) redisTemplate.opsForValue().get(key);
        Shop shop = null;
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3.不存在直接返回空
            return null;
        }
        //4.命中,先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        shop = JSONUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.判断缓存是否过期
        //5.1未过期，直接返回商铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        //5.2已过期，需要进行缓存重建
        //6缓存重建

        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        //6.2判断获取锁是否成功
        if (tryLock(lockKey)) {

            //6.3成功开启独立线程进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });


        }
        //6.4返回旧数据
        return shop;


    }


    /**
     * 使用互斥锁阻塞解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public Result queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis中获取商铺数据
        String shopJson = (String) redisTemplate.opsForValue().get(key);
        //2.判断redis是否存在商铺
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在则直接返回
            Shop shop = JSONUtil.toBean(shopJson.toString(), Shop.class);
            return Result.ok(shop);
        }
        if (shopJson != null) {
            return Result.fail("商品不存在");
        }

        //4.实现缓存重建
        //4.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;

        try {
            //4.2判断互斥锁是否获取成功
            if (!tryLock(lockKey)) {
                //4.3获取失败休眠并且重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4成功获取到锁 根据id查询数据库
            shop = getById(id);
            //模拟重建延迟
            Thread.sleep(200);
            //5.不存在返回错误
            if (shop == null) {
                redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("商品不存在");
            }
            //6.存在写入redis
            String jsonStr = JSONUtil.toJsonStr(shop);
            redisTemplate.opsForValue().set(key, jsonStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放锁
            unlock(lockKey);
        }
        return Result.ok(shop);
    }

    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }


    public Result queryWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = (String) redisTemplate.opsForValue().get(key);
        Shop shop = null;
        if (StrUtil.isNotBlank(shopJson)) {
            shop = JSONUtil.toBean(shopJson.toString(), Shop.class);
            return Result.ok(shop);
        }
        if (shopJson != null) {
            return Result.fail("商品不存在");
        }
        shop = getById(id);
        if (shop == null) {
            redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商品不存在");
        }
        String jsonStr = JSONUtil.toJsonStr(shop);
        redisTemplate.opsForValue().set(key, jsonStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商品id不能为空");
        }
        updateById(shop);
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        redisTemplate.delete(key);
        return Result.ok();
    }
}
