package com.chalkak.backend.admin.api.v1.dto.response;

import java.util.List;

public record AdminAppVersionListResponse(List<AdminAppVersionResponse> appVersions) {
}
