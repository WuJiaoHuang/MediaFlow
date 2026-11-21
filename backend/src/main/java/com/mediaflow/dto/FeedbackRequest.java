package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record FeedbackRequest(
    @NotNull @Pattern(regexp = "TEXT|AUDIO|VIDEO") String type,
    @Size(max = 4000) String content,
    @Positive Long assetId,
    @PositiveOrZero Long timecodeMs) {}
