package com.jgdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


/**
 * @author 喜欢悠然独自在
 * @version 1.0
 */
public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    //线程标识前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        //初始化脚本对象
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //设置加载的脚本路径
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //设置返回值的类型
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程的id作为value的值
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        //set key value NX EX expireTime 等同于setnx以后通过EX为该key设置一个超时时间
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(RedisConstants.KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(RedisConstants.KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
                );
    }

    /*@Override
    public void unlock() {
        //获取当前线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取当前key中存放的线程标识
        String value = stringRedisTemplate.opsForValue().get(RedisConstants.KEY_PREFIX + name);
        if (threadId.equals(value)) {
            //是当前线程的锁才释放
            //把key删除即可视为释放锁
            stringRedisTemplate.delete(RedisConstants.KEY_PREFIX + name);
        }
    }*/
}
