package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record AdminUserRoleUpdateRequest(
        @NotNull
        @JsonDeserialize(using = AdminUserRoleUpdateRequest.RoleDeserializer.class)
        @Schema(description = "변경할 역할. ARTIST는 부여, USER는 회수")
        UserRole role,
        @NotBlank
        @Schema(description = "작가 권한 변경 사유. 앞뒤 공백을 제거한 후 최대 500자")
        String reason
) {

    static final class RoleDeserializer extends ValueDeserializer<UserRole> {

        @Override
        public UserRole deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(UserRole.class, "role은 JSON 문자열이어야 합니다.");
            }
            try {
                return UserRole.valueOf(parser.getString());
            } catch (IllegalArgumentException exception) {
                return context.reportInputMismatch(UserRole.class, "role은 USER 또는 ARTIST여야 합니다.");
            }
        }
    }
}
