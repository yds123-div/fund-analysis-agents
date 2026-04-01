package com.hex.fund.service.auth;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.common.exception.BizException;
import com.hex.fund.common.exception.ErrorCode;
import com.hex.fund.common.security.JwtUtil;
import com.hex.fund.service.entity.User;
import com.hex.fund.service.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证与用户管理服务。
 * 使用 BCrypt 加密密码，Redis 记录登录失败次数实现账户锁定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 15;
    private static final String FAIL_KEY_PREFIX = "auth:fail:";
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    @Value("${jwt.secret:FundAnalysisAgents2026SecretKey!!}")
    private String jwtSecret;

    /**
     * 登录验证，成功返回 JWT Token
     */
    public String login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) throw new BizException(ErrorCode.AUTH_BAD_CREDENTIALS);
        checkAccountStatus(user);
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            recordFailure(username, user);
            throw new BizException(ErrorCode.AUTH_BAD_CREDENTIALS);
        }
        clearFailure(username, user);
        return JwtUtil.generateToken(jwtSecret, user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * 刷新 Token
     */
    public String refreshToken(String token) {
        return JwtUtil.refreshToken(jwtSecret, token);
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    public List<User> listUsers() {
        return userMapper.selectList(null);
    }

    public User createUser(String username, String password, String email, String phone, String role) {
        User user = User.builder()
                .username(username).passwordHash(BCrypt.hashpw(password))
                .email(email).phone(phone).role(role != null ? role : "USER")
                .status(1).loginFailCount(0).build();
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public void updateUser(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void deleteUser(Long id) {
        userMapper.deleteById(id);
    }

    public void resetPassword(Long id, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BizException(ErrorCode.DATA_NOT_FOUND);
        user.setPasswordHash(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    // ---- private helpers ----

    private void checkAccountStatus(User user) {
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
    }

    private void recordFailure(String username, User user) {
        String key = FAIL_KEY_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, LOCK_MINUTES, TimeUnit.MINUTES);
        if (count != null && count >= MAX_FAIL) {
            user.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            user.setLoginFailCount(count.intValue());
            userMapper.updateById(user);
            log.warn("账户 {} 因连续 {} 次登录失败被锁定 {} 分钟", username, count, LOCK_MINUTES);
        }
    }

    private void clearFailure(String username, User user) {
        redisTemplate.delete(FAIL_KEY_PREFIX + username);
        user.setLoginFailCount(0);
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
}