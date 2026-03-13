package com.justrun.todo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.justrun.todo.common.UserContext;
import com.justrun.todo.dto.auth.AuthTokenResponse;
import com.justrun.todo.dto.auth.ForgotPasswordRequest;
import com.justrun.todo.dto.auth.LoginRequest;
import com.justrun.todo.dto.auth.RefreshTokenRequest;
import com.justrun.todo.dto.auth.RegisterRequest;
import com.justrun.todo.dto.auth.ResetPasswordRequest;
import com.justrun.todo.dto.auth.UserProfileResponse;
import com.justrun.todo.entity.User;
import com.justrun.todo.exception.BizException;
import com.justrun.todo.mapper.UserMapper;
import com.justrun.todo.service.AuthService;
import com.justrun.todo.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String ACCESS_PREFIX = "todo:token:access:";
    private static final String REFRESH_PREFIX = "todo:token:refresh:";
    private static final String RESET_CODE_PREFIX = "todo:password:reset:";

    private static final long ACCESS_EXPIRE_SECONDS = 2 * 60 * 60L;
    private static final long REFRESH_EXPIRE_SECONDS = 30L * 24 * 60 * 60;
    private static final long RESET_CODE_EXPIRE_SECONDS = 10 * 60L;

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(UserMapper userMapper, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void register(RegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .last("limit 1");
        User exist = userMapper.selectOne(wrapper);
        if (exist != null) {
            throw new BizException(4002, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        userMapper.insert(user);
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        User user = findByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(4011, "用户名或密码错误");
        }
        return issueTokens(user.getId(), user);
    }

    @Override
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        String userIdText = redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
        if (!StringUtils.hasText(userIdText)) {
            throw new BizException(4012, "refresh token 已失效，请重新登录");
        }

        Long userId = Long.parseLong(userIdText);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(4041, "用户不存在");
        }

        redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        return issueTokens(userId, user);
    }

    @Override
    public void sendResetCode(ForgotPasswordRequest request) {
        User user = findByUsername(request.getUsername());
        if (user == null) {
            return;
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(RESET_CODE_PREFIX + request.getUsername(), code, RESET_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        log.info("[PasswordResetCode] username={}, code={}", request.getUsername(), code);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException(4003, "两次输入的密码不一致");
        }

        String redisCode = redisTemplate.opsForValue().get(RESET_CODE_PREFIX + request.getUsername());
        if (!StringUtils.hasText(redisCode) || !redisCode.equals(request.getCode())) {
            throw new BizException(4004, "验证码错误或已过期");
        }

        User user = findByUsername(request.getUsername());
        if (user == null) {
            throw new BizException(4041, "用户不存在");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        redisTemplate.delete(RESET_CODE_PREFIX + request.getUsername());
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (StringUtils.hasText(accessToken)) {
            redisTemplate.delete(ACCESS_PREFIX + accessToken);
        }
        if (StringUtils.hasText(refreshToken)) {
            redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        }
    }

    @Override
    public UserProfileResponse currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(4010, "未登录或登录状态失效");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(4041, "用户不存在");
        }
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getNickname());
    }

    private AuthTokenResponse issueTokens(Long userId, User user) {
        String accessToken = TokenUtil.generateToken();
        String refreshToken = TokenUtil.generateToken();

        redisTemplate.opsForValue().set(ACCESS_PREFIX + accessToken, String.valueOf(userId), ACCESS_EXPIRE_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(REFRESH_PREFIX + refreshToken, String.valueOf(userId), REFRESH_EXPIRE_SECONDS, TimeUnit.SECONDS);

        UserProfileResponse profile = new UserProfileResponse(user.getId(), user.getUsername(), user.getNickname());
        return new AuthTokenResponse(accessToken, refreshToken, ACCESS_EXPIRE_SECONDS, REFRESH_EXPIRE_SECONDS, profile);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("limit 1"));
    }
}
