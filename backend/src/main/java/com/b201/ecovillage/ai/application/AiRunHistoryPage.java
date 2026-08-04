package com.b201.ecovillage.ai.application;

import java.util.List;

public record AiRunHistoryPage(
    List<AiRunHistoryView> items,
    String nextCursor,
    boolean hasMore
) {

    public AiRunHistoryPage {
        items = List.copyOf(items);
    }
}
