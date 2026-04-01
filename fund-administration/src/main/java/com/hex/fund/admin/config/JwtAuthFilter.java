package com.hex.fund.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.JwtUtil;
import com.hex.fund.common.security.SecurityContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器。
 * 白名单路径直接放行，其余请求需携带有效 Bearer Token。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter implements Filter {

    private static final Set<String> WHITE_LIST = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/health");
    private final ObjectMapper objectMapper;
    @Value("${jwt.secret:FundAnalysisAgents2026SecretKey!!}")
    private String jwtSecret;
    @Value("${springdoc.enabled:true}")
    private boolean swaggerEnabled;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) req;
        var response = (HttpServletResponse) res;
        try {
            if (isWhiteListed(request)) {
                chain.doFilter(req, res);
                return;
            }
            String token = extractToken(request);
            if (token == null) {
                writeUnauthorized(response);
                return;
            }
            Claims claims = JwtUtil.parseToken(jwtSecret, token);
            SecurityContext.setCurrentUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class));
            chain.doFilter(req, res);
        } catch (Exception e) {
            log.debug("JWT 认证失败: {}", e.getMessage());
            writeUnauthorized(response);
        } finally {
            SecurityContext.clear();
        }
    }

    private boolean isWhiteListed(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.equals("/") || uri.equals("/index.html") || uri.equals("/favicon.ico")
                || uri.startsWith("/assets/")) return true;
        if (!uri.startsWith("/api/") && !uri.startsWith("/v3/") && !uri.startsWith("/swagger-ui")) return true;
        // Swagger 仅在启用时放行
        if (swaggerEnabled && (uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui"))) return true;
        return WHITE_LIST.stream().anyMatch(uri::startsWith);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        // SSE 等不支持自定义 Header 的场景，从 query 参数取 token
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) return queryToken;
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(60004, "invalid or expired token")));
    }
}