package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record UpdateUserRequest(
    @NotBlank @Size(max = 100) String displayName, @NotNull @Min(0) @Max(1) Integer status) {}
