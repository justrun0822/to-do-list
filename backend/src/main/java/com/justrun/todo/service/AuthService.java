package com.justrun.todo.service;

import com.justrun.todo.dto.auth.AuthTokenResponse;
import com.justrun.todo.dto.auth.ForgotPasswordRequest;
import com.justrun.todo.dto.auth.LoginRequest;
import com.justrun.todo.dto.auth.RefreshTokenRequest;
import com.justrun.todo.dto.auth.RegisterRequest;
import com.justrun.todo.dto.auth.ResetPasswordRequest;
import com.justrun.todo.dto.auth.UserProfileResponse;

public interface AuthService {
    void register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);

    AuthTokenResponse refresh(RefreshTokenRequest request);

    void sendResetCode(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void logout(String accessToken, String refreshToken);

    UserProfileResponse currentUser();
}
