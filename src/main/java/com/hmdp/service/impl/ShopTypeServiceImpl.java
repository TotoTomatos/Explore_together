package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private RedisTemplate redisTemplate;
    @Override
    public Result queryTypeList() {
        String shopTypeListStr = (String)redisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_KEY);
        if(StrUtil.isNotBlank(shopTypeListStr)){
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListStr, ShopType.class);
            return Result.ok(shopTypeList);
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        String jsonStr = JSONUtil.toJsonStr(shopTypeList);
        redisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_KEY, jsonStr);
        return Result.ok(shopTypeList);
    }
}
