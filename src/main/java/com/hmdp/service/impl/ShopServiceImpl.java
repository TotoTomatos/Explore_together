package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        // Result result = queryWithPassThrough(id);

        //解决缓存穿透+缓存击穿
        Result result = queryWithMutex(id);
        return result;
    }

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
