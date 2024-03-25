package com.jgdp.utils;

public class RedisConstants {

    //登录验证码key
    public static final String LOGIN_CODE_KEY = "login:code:";
    //登录验证码key过期时间
    public static final Long LOGIN_CODE_TTL = 2L;

    //登录用户key
    public static final String LOGIN_USER_KEY = "login:token:";
    //登录用户key过期时间
    public static final Long LOGIN_USER_TTL = 30L;

    //缓存空值过期时间
    public static final Long CACHE_NULL_TTL = 2L;

    //店铺key过期时间
    public static final Long CACHE_SHOP_TTL = 30L;
    //店铺key
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    //店铺类型key
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:";

    //互斥锁key
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    //互斥锁key过期时间
    public static final Long LOCK_SHOP_TTL = 10L;

    //自增长key
    public static final String INCREMENT_KEY = "icr:";

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
