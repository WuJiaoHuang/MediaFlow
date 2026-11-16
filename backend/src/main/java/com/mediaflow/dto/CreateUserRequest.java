package com.mediaflow.dto;

import jakarta.validation.constraints.*;
import java.util.Set;

public record CreateUserRequest(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}") String username,
    @NotBlank
        @Size(min = 12, max = 64)
        @Pattern(regexp = "[\\x21-\\x7E]+", message = "密码须为 ASCII 可见字符")
        String password,
    @NotBlank @Size(max = 100) String displayName,
    @NotNull @Size(min = 1, max = 3) Set<@NotBlank String> roles) {}
