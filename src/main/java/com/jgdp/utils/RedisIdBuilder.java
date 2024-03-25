package com.jgdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author 喜欢悠然独自在
 * @version 1.0
 */
@Component
public class RedisIdBuilder {

    //开始时间戳（2024.01.01 00:00:00）
    private static final long BEGIN_TIMESTAMP = 1704067200l;
    //序列号位数
    private static final int COUNT_BIT = 32;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        //2.2.自增长
        long count = stringRedisTemplate
                .opsForValue()
                .increment(RedisConstants.INCREMENT_KEY + keyPrefix + ":" + date);

        //3.拼接并返回
        //3.1.时间戳一开始在低位，我们要让时间戳位移到高位，向左位移32位，为32位的序列号腾出空间
        //3.2.使用或运算对每一位进行比较然后把序列号拼接到腾出的空位中
        return timestamp << COUNT_BIT | count;
    }

}
