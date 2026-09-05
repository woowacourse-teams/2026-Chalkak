package com.chalkak.backend.admin.repository;

public record AdminUserQueryCriteria(
        AdminUserQueryStatus status,
        String email,
        AdminUserQuerySort sort
) {
}
