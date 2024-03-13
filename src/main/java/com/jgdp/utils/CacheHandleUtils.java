package com.jgdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author 喜欢悠然独自在
 * @version 1.0
 */
@Slf4j
@Component
public class CacheHandleUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 把Java对象转成JSON字符串存入设有超时时间的String类型的key中
     *
     * @param key
     * @param value
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     */
    public void set(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        //1.写入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, timeUnit);
    }

    /**
     * 把Java对象转成JSON字符串存入设有超时时间的String类型的key中（逻辑过期专用）
     *
     * @param key
     * @param data
     * @param expireTime
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object data, Long expireTime, TimeUnit timeUnit) {
        //1.设置RedisData对象
        RedisData redisData = new RedisData();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        //2.写入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    public boolean tryLock(String key) {
        // 基于redis中的setnx key value来实现互斥锁
        // setnx命令：当前key存在时，创建key失败
        //1.使用setnx命令来创建key value，设置过期时间（出现异常导致不释放锁的兜底方案）
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //2.返回执行结果，true为获取锁成功，false为获取锁失败
        // 防止自动拆箱时出现空指针，所以使用工具包对数据进行拆箱处理，除了true就返回true，其他情况都返回false
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    public void unLock(String key) {
        //把key删除即可视为释放锁
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存穿透
     *
     * @param keyPrefix  key前缀
     * @param id
     * @param type       返回值类型
     * @param dbFallback 查询数据库
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     * @param <R>        返回值泛型
     * @param <ID>       id参数泛型
     * @return
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
            Long expireTime, TimeUnit timeUnit) {

        //1.从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断是否为空值（JSON字符串为空且不等于null就说明命中了空字符串""）
        if (json != null) {
            //是空字符串就直接返回错误信息
            return null;
        }

        //4.不存在，查询数据库
        R r = dbFallback.apply(id);

        //5.判断是否存在
        if (r == null) {
            //6.如果数据库中也不存在，则需要做防缓存穿透处理
            // 缓存空值（空字符串），设置空值过期时间，然后返回错误信息
            this.set(key, "", RedisConstants.CACHE_NULL_TTL, timeUnit);
            return null;
        }

        //7.存在，将商铺数据写入redis，使用超时剔除策略
        this.set(key, r, expireTime, timeUnit);

        //8.返回
        return r;
    }

    /**
     * 缓存击穿（逻辑过期）
     *
     * @param keyPrefix  key前缀
     * @param id
     * @param type       返回值类型
     * @param dbFallback 查询数据库方法
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     * @param <ID>       id参数泛型
     * @param <R>        返回值泛型
     * @return
     */
    public <ID, R> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
            Long expireTime, TimeUnit timeUnit) {

        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY;

        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //3.不存在，返回错误信息
            return null;
        }

        //4.存在
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime time = redisData.getExpireTime();

        //5.判断缓存是否过期
        if (time.isAfter(LocalDateTime.now())) {
            //6.未过期，直接返回
            return r;
        }

        //7.过期，进行缓存重建
        //7.1.获取互斥锁
        boolean isLock = tryLock(lockKey);

        //7.2.判断是否获取锁成功
        if (isLock) {
            //7.3.获取锁成功，开启独立线程查询数据库
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入缓存
                    this.setWithLogicalExpire(key, r1, expireTime, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //7.4.获取锁成功或失败，都返回旧的信息
        return r;
    }

    public <ID, R> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
            Long expireTime, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY;

        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在，直接返回
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        // 判断是否为空值（不等于null就说明命中了空字符串""）
        if (json != null) {
            //是空字符串就直接返回错误信息
            return null;
        }

        //4.缓存重建
        R r = null;
        try {
            //4.1.获取互斥锁
            boolean isLock = tryLock(lockKey);

            //4.2.判断是否获取锁成功
            if (!isLock) {
                //4.3.获取锁失败，休眠一段时间，继续尝试获取
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type,
                        dbFallback, expireTime, timeUnit);
            }

            //4.4.获取锁成功，查询数据库
            r = dbFallback.apply(id);

            //5.判断是否存在
            if (r == null) {
                //6.不存在，缓存空值（空字符串），设置空值过期时间，然后返回错误信息
                this.set(key, "", RedisConstants.CACHE_NULL_TTL, timeUnit);
                return null;
            }

            //7.存在，将商铺数据写入redis，使用超时剔除策略
            this.set(key, r, expireTime, timeUnit);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //8.释放锁（无论是否执行成功，都应该释放锁）
            unLock(lockKey);
        }

        //9.返回
        return r;
    }

}
