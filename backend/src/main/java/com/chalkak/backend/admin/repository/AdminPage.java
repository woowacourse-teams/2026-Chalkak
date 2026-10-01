package com.chalkak.backend.admin.repository;

import java.util.List;

public record AdminPage<T>(
        List<T> items,
        int currentPage,
        int pageSize,
        boolean hasNext) {

    public AdminPage {
        items = List.copyOf(items);
    }
}
