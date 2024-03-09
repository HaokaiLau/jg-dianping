package com.jgdp.config;

import com.jgdp.utils.LoginInterceptor;
import com.jgdp.utils.RefreshTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

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
