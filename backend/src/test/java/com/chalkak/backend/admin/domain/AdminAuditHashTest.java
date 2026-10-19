package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminAuditHashTest {

    @ParameterizedTest
    @CsvSource({"'',e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "abc,ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "제목📷,f0ccf736a6a6bf9c58fd411995bcdd8df59593c8c6078f533c0d4be87c5f4ad1"})
    @DisplayName("감사 해시는 UTF-8 문자열을 SHA-256 소문자 16진수로 반환한다")
    void createSha256_knownUtf8Input_returnsExpectedHex(String value, String expected) {
        // When
        String result = AdminAuditHash.createSha256(value);

        // Then
        assertThat(result).isEqualTo(expected);
    }
}
