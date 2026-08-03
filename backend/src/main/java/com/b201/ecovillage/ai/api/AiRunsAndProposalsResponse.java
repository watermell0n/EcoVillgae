package com.b201.ecovillage.ai.api;

import java.util.List;

import com.b201.ecovillage.ai.application.AiRunHistoryView;

public record AiRunsAndProposalsResponse(List<AiRunHistoryView> items) {

    public AiRunsAndProposalsResponse {
        items = List.copyOf(items);
    }
}
