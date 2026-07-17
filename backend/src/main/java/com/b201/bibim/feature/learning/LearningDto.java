package com.b201.bibim.feature.learning;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public final class LearningDto {

    private LearningDto() {
    }

    public record PageResponse(
            UUID pageId,
            String topicCode,
            String topicTitle,
            int pageOrder,
            String title,
            String content,
            List<String> keyPoints
    ) {
    }

    public record GuidedLearningResponse(
            String stage,
            String mode,
            long syncVersion,
            int currentIndex,
            List<PageResponse> pages,
            PageResponse currentPage
    ) {
    }

    public record ChangePageRequest(
            @NotNull(message = "이동할 교육 페이지가 필요합니다.")
            UUID pageId,
            @NotNull(message = "현재 방 버전이 필요합니다.")
            Integer expectedVersion
    ) {
    }

    public record TopicResponse(
            UUID topicId,
            String code,
            String title,
            String description,
            int pageCount
    ) {
    }

    public record TopicDetailResponse(
            UUID topicId,
            String code,
            String title,
            String description,
            List<PageResponse> pages
    ) {
    }
}
