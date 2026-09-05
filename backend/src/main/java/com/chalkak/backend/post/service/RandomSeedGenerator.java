package com.chalkak.backend.post.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RandomSeedGenerator {

    private static final int SEED_BYTE_LENGTH = 4;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateRandomSeed() {
        byte[] seed = new byte[SEED_BYTE_LENGTH];
        secureRandom.nextBytes(seed);
        return HexFormat.of().formatHex(seed);
    }
}
