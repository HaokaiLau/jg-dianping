package com.jgdp.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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
