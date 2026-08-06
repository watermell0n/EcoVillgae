package com.b201.ecovillage.quiz.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageLessonStartRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 8_000_000) String imageUrl
) {
}
