package com.chalkak.backend.jev.service;

import com.chalkak.backend.jev.domain.JevPromptConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Component
@RequiredArgsConstructor
public class JevClassificationKeyFactory {

    private final ObjectMapper objectMapper;

    public String create(
            UUID topicId,
            String topic,
            String model,
            JevPromptConfiguration configuration,
            String scope
    ) {
        String canonical = objectMapper.writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(configuration);
        String content = scope + "\n" + topicId + "\n" + topic + "\n" + model + "\n" + canonical;
        return hash(content);
    }

    public String createRecoveryKey(String originalKey) {
        return hash("production-recovery:\n" + originalKey);
    }

    private String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
