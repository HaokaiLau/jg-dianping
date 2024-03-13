# Java项目实践——鸡哥点评

## 1.项目介绍

### 1.1 项目介绍

​	本项目涵盖了Redis的各种数据结构和命令，Redis的各种常见Java客户端的应用和最佳实践。还有Redis在企业中的应用方案，例如共享session、缓存及缓存更新策略、分布式锁、消息队列、秒杀等等场景。另外还有Redis的主从、哨兵、集群等的搭建和原理，使用运维过程中的最佳实践方案。最后还会深入学习Redis底层原理、网络模型、通信模型、内存淘汰策略等内容

### 1.2 项目内容

1.共享session登录问题

- 这部分会使用Redis来处理Session不共享问题。

2.商户查询缓存

- 这部分要理解缓存击穿，缓存穿透，缓存雪崩等问题，对于这些概念的理解不仅仅是停留在概念上，更是能在代码中看到对应的内容。

3.优惠劵秒杀

- 这部分我们可以学会Redis的计数器功能，结合Lua脚本完成高性能的Redis操作，同时学会Redis分布式锁的原理，包括Redis的三种消息队列。

- 达人探店
  - 基于List来完成点赞列表的操作，同时基于SortedSet来完成点赞的排行榜功能。
- 好友关注
  - 基于Set集合的关注、取消关注，共同关注等等功能。
- 附近商户
  - 利用Redis的GEOHash结构来完成对于地理坐标的操作。
- 用户签到
  - 使用Redis的BitMap数据实现签到以及统计功能。
- UV统计
  - 主要是使用Redis的HyperLogLog结构来完成统计功能。

### 1.3 技术选型

**后端：**

1. 对Maven工程的结构和特点需要有一定的理解
2. git: 版本控制工具, 在团队协作中, 使用该工具对项目中的代码进行管理。
3. junit：单元测试工具，开发人员功能实现完毕后，需要通过junit对功能进行单元测试。
4. SpringBoot： 快速构建Spring项目, 采用 "约定优于配置" 的思想, 简化Spring项目的配置开发。
5. SpringMVC：SpringMVC是spring框架的一个模块，springmvc和spring无需通过中间整合层进行整合，可以无缝集成。
6. MySQL： 关系型数据库, 本项目的核心业务数据都会采用MySQL进行存储。
7. Redis： 基于key-value格式存储的内存数据库, 访问速度快, 经常使用它做缓存。
8. Mybatis： 本项目持久层将会使用Mybatis开发。
9. Mybatis Plus：[MyBatis-Plus (opens new window)](https://github.com/baomidou/mybatis-plus)（简称 MP）是一个 [MyBatis (opens new window)](https://www.mybatis.org/mybatis-3/)的增强工具，在 MyBatis 的基础上只做增强不做改变，为简化开发、提高效率而生。

**前端：**

本项目中在构建系统管理后台的前端页面，我们会用到H5、Vue.js等技术。

## 2.项目重点功能的实现思路以及代码

### 2.1共享session登录问题

session在服务器集群中很难实现session共享的效果，所以我们采取Redis缓存的方法解决这一问题

#### 2.1.1 完善登录认证功能

1.发送手机验证码功能

```java
/**
     * 发送手机验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }

        //3.如果合法，生成验证码（使用hutool中的工具类生成）
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到session
        //session.setAttribute("code",code);

        //4.保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码（这里暂时没有调用其他api进行验证码的发送）
        log.debug("发送短信验证码成功!验证码为:{}", code);

        //6.返回成功信息
        return Result.ok();
    }
```

2.登录功能

```java
/**
     * 登录功能
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号是否合法
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }

        //3.校验验证码是否正确
        // 获取redis中保存的验证码和前端发送来的验证码进行比对
        //Object cacheCode = session.getAttribute("code");
        Object cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            //4.验证码过期或者不正确，返回错误信息
            return Result.fail("验证码错误!");
        }

        //5.根据手机号查询数据库 select * from tb_user where phone = ?
        //使用mp提供的api实现查询功能
        User user = query().eq("phone", phone).one();

        //6.判断用户是否存在
        if (user == null) {
            //7.不存在，创建新用户，保存到数据库
            user = createWithPhone(phone);
        }

        //8.保存用户信息到session中
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //注:与jwt令牌不同的是，基于session实现的登录功能无需返回登录凭证
        //8.保存用户信息到redis中
        //8.1 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);

        //8.2 将User对象转成Hash存储
        Map<String, Object> userMap = BeanUtil.beanToMap(user, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //8.3 存储
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        //8.4设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //9.返回成功信息
        return Result.ok(token);
    }

    private User createWithPhone(String phone) {
        //1.创建新用户
        User user = new User();
        //2.为对象属性赋值
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //3.保存用户
        save(user);
        return user;
    }
```

4.自定义令牌刷新拦截器

```java
/**
 * @author 喜欢悠然独自在
 * @version 1.0
 * 令牌刷新拦截器（实现 HandlerInterceptor接口）
 * 令用户在请求无需登录的页面时也能刷新令牌保持登录状态
 */
@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 前置拦截
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        /*
        用户未登录/token过期（判断是否有token或者redis中的登录信息是否过期）
         */

        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }

        //2.基于token从redis中获取数据
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        //3.判断用户是否存在
        if (userMap.isEmpty()) {
            return true;
        }

        /*
        以下是token未过期才会执行的逻辑（用户是已登录的状态，正在浏览无需登录校验的页面，刷新令牌有效期）
         */

        //5.把Map转成UserDTO对象
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //6.存在，把用户信息保存到ThreadLocal
        UserHolder.saveUser(user);

        //7.刷新token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.放行
        return true;
    }

    /**
     * controller执行后
     * @param request
     * @param response
     * @param handler
     * @param modelAndView
     * @throws Exception
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        //销毁ThreadLocal中存储的信息，防止内存泄露
        UserHolder.removeUser();
    }
}
```

5.自定义登录校验拦截器

```java
/**
 * @author 喜欢悠然独自在
 * @version 1.0
 * 登录校验拦截器（实现 HandlerInterceptor接口）
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 前置拦截
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截，依据就是ThreadLocal中是否有用户
        if (UserHolder.getUser() == null) {
            //ThreadLocal中没有用户，说明没登录，拦截
            log.info("用户未登录!");
            response.setStatus(401);
            return false;
        }
        //有用户，说明已登录，放行
        return true;
    }

}
```

6.定义WebMVC配置类，注册拦截器

```java
/**
 * @author 喜欢悠然独自在
 * @version 1.0
 * 定义WebMvc配置类
 * 注册web层相关组件
 * （实现 WebMvcConfiguration接口/继承 WebMvcConfigurationSupport）
 */
@Slf4j
@Configuration
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;

    /**
     * 注册登陆校验拦截器
     *
     * @param registry
     */
    @Override
    protected void addInterceptors(InterceptorRegistry registry) {

        log.info("开始注册令牌刷新拦截器...");
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);

        log.info("开始注册登录校验拦截器...");
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/user/**",
                        "/blog/**"
                )
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot"
                )
                .order(1);
    }
}
```

实现步骤：

1. 前端发送获取验证码请求，后端对手机号进行合法性校验后生成验证码，以用户手机号为key，验证码为value存入redis中，并且设置过期时间，把验证码响应给前端。
2. 前端发起登录请求，后端对手机号进行合法性校验后，从redis中基于手机号（key）取出验证码（value），用前端传来的验证码和redis中的验证码进行校验后，根据手机号查询数据库，获取用户数据，判断用户是否为新用户，是则注册（新增用户），然后将用户对象转成Map，使用uuid随机生成一个token，token为key，Map为value，以hash数据类型缓存到redis，并设置过期时间，响应token给前端。
3. 为了使用户有更好的使用体验，减少用户在浏览无需登陆校验页面时token频繁过期的问题，我们使用了两层拦截器，外层拦截器为令牌拦截器，内层拦截器为登录校验拦截器，并且在令牌拦截器中添加了token刷新的逻辑。
4. 令牌拦截器：拦截全路径，当用户未登录浏览无登陆校验页面时，直接放行；如果用户是已登录的状态就基于token从redis中取出用户数据，存入ThreadLocal中，然后刷新token有效期，再放行。
5. 登录校验拦截器：拦截需要登录校验的请求路径，从令牌拦截器中放行的请求，只有两种结果，一种是没有往ThreadLocal中存数据的，一种是存入了用户数据的，所以只需判断ThreadLocal中是否有用户数据即可知道发起该请求的用户是否已经登录。

### 3.项目总结

参考视频：[黑马点评](https://www.bilibili.com/video/BV1NV411u7GE?p=1&vd_source=2efbbaa09c22a07508c7c52b78951722)

