package com.justrun.todo.interceptor;

import com.justrun.todo.common.UserContext;
import com.justrun.todo.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String ACCESS_PREFIX = "todo:token:access:";

    private final StringRedisTemplate redisTemplate;

    public AuthInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (!StringUtils.hasText(auth)) {
            throw new BizException(4010, "未登录或登录状态失效");
        }

        String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        if (!StringUtils.hasText(token)) {
            throw new BizException(4010, "未登录或登录状态失效");
        }

        String userIdText = redisTemplate.opsForValue().get(ACCESS_PREFIX + token);
        if (!StringUtils.hasText(userIdText)) {
            throw new BizException(4010, "未登录或登录状态失效");
        }

        UserContext.setUserId(Long.parseLong(userIdText));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
