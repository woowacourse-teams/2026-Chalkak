package com.chalkak.backend.auth.infrastructure.bootstrap;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SocialAccountSubjectHmacBackfill implements ApplicationRunner {

    private final SocialAccountRepository socialAccountRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        socialAccountRepository.findAllBySubjectHmacIsNull()
                .forEach(this::backfill);
    }

    private void backfill(SocialAccount socialAccount) {
        String subjectHmac = fingerprintEncoder.encode(
                socialAccount.getProvider(),
                socialAccount.getSubject());
        socialAccount.backfillSubjectHmac(subjectHmac);
        socialAccountRepository.save(socialAccount);
    }
}
