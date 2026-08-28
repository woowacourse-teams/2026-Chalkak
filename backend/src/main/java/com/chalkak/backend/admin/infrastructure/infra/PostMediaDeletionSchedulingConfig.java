package com.chalkak.backend.admin.infrastructure.infra;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("!test")
public class PostMediaDeletionSchedulingConfig {
}
