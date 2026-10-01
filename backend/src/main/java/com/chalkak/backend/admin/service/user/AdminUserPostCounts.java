package com.chalkak.backend.admin.service.user;

public record AdminUserPostCounts(
        long pending,
        long approved,
        long rejected) {
}
