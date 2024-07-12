package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
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
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = (String)redisTemplate.opsForValue().get(key);
        Shop shop = null;
        if(StrUtil.isNotBlank(shopJson)){
            shop = JSONUtil.toBean(shopJson.toString(), Shop.class);
            return Result.ok(shop);
        }
        shop = getById(id);
        if(shop == null){
            return Result.fail("商品不存在");
        }
        String jsonStr = JSONUtil.toJsonStr(shop);
        redisTemplate.opsForValue().set(key, jsonStr, RedisConstants.CACHE_SHOP_TTL);
        return Result.ok(shop);
    }
}
