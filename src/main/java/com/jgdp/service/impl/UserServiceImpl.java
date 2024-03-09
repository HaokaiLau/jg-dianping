package com.jgdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jgdp.dto.LoginFormDTO;
import com.jgdp.dto.Result;
import com.jgdp.entity.User;
import com.jgdp.mapper.UserMapper;
import com.jgdp.service.IUserService;
import com.jgdp.utils.RedisConstants;
import com.jgdp.utils.RegexUtils;
import com.jgdp.utils.SystemConstants;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
}
