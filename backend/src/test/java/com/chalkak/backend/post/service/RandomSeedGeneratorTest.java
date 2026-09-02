package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandomSeedGeneratorTest {

    private final RandomSeedGenerator randomSeedGenerator = new RandomSeedGenerator();

    @Test
    @DisplayName("8자리 소문자 16진수 랜덤 시드를 생성한다")
    void generateRandomSeed_returnsEightCharacterHexString() {
        // When
        String result = randomSeedGenerator.generateRandomSeed();

        // Then
        assertThat(result).matches("[0-9a-f]{8}");
    }
}
