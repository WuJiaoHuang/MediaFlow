package com.mediaflow.controller;

import com.mediaflow.common.ApiResponse;
import com.mediaflow.dto.LoginRequest;
import com.mediaflow.security.*;
import com.mediaflow.service.UserService;
import com.mediaflow.vo.UserView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserService users;
  private final JwtService jwt;

  public AuthController(UserService users, JwtService jwt) {
    this.users = users;
    this.jwt = jwt;
  }

  public record LoginResult(String accessToken, String tokenType, long expiresIn, UserView user) {}

  @PostMapping("/login")
  public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest req) {
    var u = users.login(req.username(), req.password());
    return ApiResponse.ok(new LoginResult(jwt.issue(u.id()), "Bearer", jwt.ttl(), u));
  }

  @GetMapping("/me")
  public ApiResponse<UserView> me(@AuthenticationPrincipal CurrentUser principal) {
    return ApiResponse.ok(principal.user());
  }
}
