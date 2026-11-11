package com.chalkak.backend.user.api.v1.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ArtistApplicationRequest(
        @NotNull(message = "원작 확약 동의 여부는 필수입니다.")
        @AssertTrue(message = "원작 확약에 동의해야 합니다.")
        Boolean artistTermsAgreed
) {
}
