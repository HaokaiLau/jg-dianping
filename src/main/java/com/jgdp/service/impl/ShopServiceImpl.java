package com.jgdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jgdp.dto.Result;
import com.jgdp.entity.Shop;
import com.jgdp.mapper.ShopMapper;
import com.jgdp.service.IShopService;
import com.jgdp.utils.CacheHandleUtils;
import com.jgdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheHandleUtils cacheHandleUtils;
//    //创建线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询商铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透问题
        //Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheHandleUtils.queryWithPassThrough(
//                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿问题
        //Shop shop = queryWithMutex(id);
        Shop shop = cacheHandleUtils.queryWithMutex(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //逻辑过期解决缓存击穿问题
        //Shop shop = queryWithLogicalExpire(id);
//        Shop shop = cacheHandleUtils.queryWithLogicalExpire(
//                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //判断是否查到数据
        if (shop == null) {
            return Result.fail("店铺不存在!!");
        }

        //8.返回
        return Result.ok();
    }

    /**
     * 更新店铺信息
     *
     * @param shop
     * @return
     */
    @Transactional//使用事务保证数据的一致性
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        //判断id是否存在
        if (id == null) {
            //不存在，返回错误信息
            return Result.fail("当前店铺id不存在!!");
        }
        //1.更新数据库
        updateById(shop);

        //2.删除redis缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        //3.返回
        return Result.ok();
    }

    /**
     * 根据id查询店铺数据防止缓存穿透的解决方案
     *
     * @param id
     * @return
     */
    /*public Shop queryWithPassThrough(Long id) {
        //1.从redis中查询商铺缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否为空值（JSON字符串为空且不等于null就说明命中了空字符串""）
        if (shopJson != null) {
            //是空字符串就直接返回错误信息
            return null;
        }

        //4.不存在，查询数据库
        Shop shop = getById(id);

        //5.判断是否存在
        if (shop == null) {
            //6.如果数据库中也不存在，则需要做防缓存穿透处理
            // 缓存空值（空字符串），设置空值过期时间，然后返回错误信息
            stringRedisTemplate.opsForValue().set(shopKey, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //7.存在，将商铺数据写入redis，使用超时剔除策略
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //8.返回
        return shop;
    }*/

    /**
     * 根据id查询店铺数据使用互斥锁防止缓存击穿
     *
     * @param id
     * @return
     */
    /*public Shop queryWithMutex(Long id) {

        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY;

        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否为空值（不等于null就说明命中了空字符串""）
        if (shopJson != null) {
            //是空字符串就直接返回错误信息
            return null;
        }

        //4.缓存重建
        Shop shop = null;
        try {
            //4.1.获取互斥锁
            boolean isLock = tryLock(lockKey);

            //4.2.判断是否获取锁成功
            if (!isLock) {
                //4.3.获取锁失败，休眠一段时间，继续尝试获取
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //4.4.获取锁成功，查询数据库
            shop = getById(id);

            //5.判断是否存在
            if (shop == null) {
                //6.不存在，缓存空值（空字符串），设置空值过期时间，然后返回错误信息
                stringRedisTemplate.opsForValue().set(shopKey, "",
                        RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //7.存在，将商铺数据写入redis，使用超时剔除策略
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //8.释放锁（无论是否执行成功，都应该释放锁）
            unLock(lockKey);
        }

        //9.返回
        return shop;
    }*/

    /**
     * 根据id查询店铺数据使用逻辑过期防止缓存击穿
     * 由于我们已经对缓存进行了预热，所以不用考虑缓存穿透的问题
     *
     * @param id
     * @return
     */
    /*public Shop queryWithLogicalExpire(Long id) {

        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY;

        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3.不存在，返回错误信息
            return null;
        }

        //4.存在
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //6.未过期，直接返回
            return shop;
        }

        //7.过期，进行缓存重建
        //7.1.获取互斥锁
        boolean isLock = tryLock(lockKey);

        //7.2.判断是否获取锁成功
        if (isLock) {
            //7.3.获取锁成功，开启独立线程查询数据库
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.savaShopToRedis(id, RedisConstants.CACHE_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //7.4.获取锁成功或失败，都返回旧的信息
        return shop;
    }*/

    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    /*public boolean tryLock(String key) {
        // 基于redis中的setnx key value来实现互斥锁
        // setnx命令：当前key存在时，创建key失败
        //1.使用setnx命令来创建key value，设置过期时间（出现异常导致不释放锁的兜底方案）
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //2.返回执行结果，true为获取锁成功，false为获取锁失败
        // 防止自动拆箱时出现空指针，所以使用工具包对数据进行拆箱处理，除了true就返回true，其他情况都返回false
        return BooleanUtil.isTrue(flag);
    }*/

    /**
     * 释放锁
     *
     * @param key
     */
    /*public void unLock(String key) {
        //把key删除即可视为释放锁
        stringRedisTemplate.delete(key);
    }*/

    /**
     * 把店铺数据手动添加到缓存中做缓存预热
     *
     * @param id
     */
    /*public void savaShopToRedis(Long id, Long expireSeconds) {
        //1.查询数据库
        Shop shop = getById(id);

        //2.封装成RedisData对象，设置好逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireSeconds));

        //3.写入Redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/
}
