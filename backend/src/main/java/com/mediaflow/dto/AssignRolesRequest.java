package com.mediaflow.dto;

import jakarta.validation.constraints.*;
import java.util.Set;

public record AssignRolesRequest(@NotNull @Size(min = 1, max = 3) Set<@NotBlank String> roles) {}
