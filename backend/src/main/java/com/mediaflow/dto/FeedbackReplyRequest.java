package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public record FeedbackReplyRequest(
    @NotNull @Min(0) Integer version, @NotBlank @Size(max = 4000) String content) {}
