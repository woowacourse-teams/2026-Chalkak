package com.chalkak.backend.aireview.repository;

import com.chalkak.backend.aireview.domain.AiPromptVersion;
import java.util.List;

public record AiPromptVersionPage(
        List<AiPromptVersion> items,
        int currentPage,
        int pageSize,
        boolean hasNext) {

    public AiPromptVersionPage {
        items = List.copyOf(items);
    }
}
