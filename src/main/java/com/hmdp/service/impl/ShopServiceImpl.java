package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Struct;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        Shop shop=queryWithLogicalExpire(id);
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    public boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id){
        //从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)){
            //未命中，返回空
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData=JSONUtil.toBean(shopJson,RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime=redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回
            return shop;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lockKey);
        //判断是否获取锁成功
        if (isLock){
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }
        return shop;
    }
    public Shop queryWithMutex(Long id){
        //从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        if (shopJson!=null){
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        Shop shop= null;
        try {
            boolean isLock=tryLock(lockKey);
            //判断获取是否成功
            if (!isLock){
                //休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功，根据id查询数据库
            shop = getById(id);
            //不存在，返回错误
            if (shop==null) {
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    public void saveShop2Redis(Long id,Long expireSeconds){
        //查询店铺数据
        Shop shop=getById(id);
        //封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    @Override
    @Transactional //确保数据一致性和完整性
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
