package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Size(max = 64) @Pattern(regexp = "[\\x21-\\x7E]+") String password) {}
