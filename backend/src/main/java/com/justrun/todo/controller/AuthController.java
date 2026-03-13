package com.justrun.todo.controller;

import com.justrun.todo.common.ApiResponse;
import com.justrun.todo.dto.auth.AuthTokenResponse;
import com.justrun.todo.dto.auth.ForgotPasswordRequest;
import com.justrun.todo.dto.auth.LoginRequest;
import com.justrun.todo.dto.auth.RefreshTokenRequest;
import com.justrun.todo.dto.auth.RegisterRequest;
import com.justrun.todo.dto.auth.ResetPasswordRequest;
import com.justrun.todo.dto.auth.UserProfileResponse;
import com.justrun.todo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("注册成功", null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/password/reset-code")
    public ApiResponse<Map<String, String>> sendResetCode(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.sendResetCode(request);
        return ApiResponse.success("验证码已发送", Map.of("note", "开发环境验证码已写入 redis，可直接查看日志/redis"));
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success("密码重置成功", null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        String accessToken = authorization == null ? null : authorization.replace("Bearer ", "");
        authService.logout(accessToken, refreshToken);
        return ApiResponse.success("退出成功", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.success(authService.currentUser());
    }
}
