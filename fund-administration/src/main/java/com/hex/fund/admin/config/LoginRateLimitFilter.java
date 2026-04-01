package com.hex.fund.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex.fund.common.model.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 登录接口 IP 级别限流过滤器。
 * 同一 IP 每分钟最多 10 次登录请求，超出返回 429。
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class LoginRateLimitFilter implements Filter {

    private static final int MAX_REQUESTS = 10;
    private static final int WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "rate:login:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) req;
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/auth/login".equals(request.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }
        String ip = getClientIp(request);
        String key = KEY_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > MAX_REQUESTS) {
            var response = (HttpServletResponse) res;
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.fail(42900, "请求过于频繁，请稍后再试")));
            return;
        }
        chain.doFilter(req, res);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null ? realIp : request.getRemoteAddr();
    }
}
