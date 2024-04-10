package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        List<String> shopTypeList =stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_LIST_KEY,0,-1);
        if (CollectionUtil.isNotEmpty(shopTypeList)){
            List<ShopType> types= JSONUtil.toList(shopTypeList.get(0),ShopType.class);
            return Result.ok(types);
        }
        List<ShopType> typeList=query().orderByAsc("sort").list();
        if (CollectionUtil.isEmpty(typeList)){
            return Result.fail("列表信息不存在");
        }

        String jsonStr=JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOP_LIST_KEY,jsonStr);
        return Result.ok(typeList);
    }
}
