package com.chalkak.backend.auth.infrastructure.bootstrap;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class SocialAccountSubjectHmacBackfillTest {

    private static final String SUBJECT = "google-subject";
    private static final String SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Mock
    private SocialAccount socialAccount;

    @InjectMocks
    private SocialAccountSubjectHmacBackfill backfill;

    @Test
    @DisplayName("HMAC이 없는 기존 소셜 계정을 찾아 HMAC을 저장한다")
    void run_missingSubjectHmac_backfillsSocialAccount() {
        // Given
        given(socialAccountRepository.findAllBySubjectHmacIsNull())
                .willReturn(List.of(socialAccount));
        given(socialAccount.getProvider()).willReturn(SocialProvider.GOOGLE);
        given(socialAccount.getSubject()).willReturn(SUBJECT);
        given(fingerprintEncoder.encode(SocialProvider.GOOGLE, SUBJECT))
                .willReturn(SUBJECT_HMAC);

        // When
        backfill.run(new DefaultApplicationArguments(new String[0]));

        // Then
        verify(socialAccount).backfillSubjectHmac(SUBJECT_HMAC);
        verify(socialAccountRepository).save(socialAccount);
    }
}
