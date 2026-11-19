package com.chalkak.backend.jev.service;

import com.chalkak.backend.jev.domain.JevChoiceAnswer;
import com.chalkak.backend.jev.domain.JevChoiceQuestion;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevEvaluation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JevResponseParser {

    private final ObjectMapper objectMapper;

    public JevEvaluation parse(
            String rawResponse,
            String model,
            Map<String, JevChoiceQuestion> questions
    ) {
        ProviderResponse response;
        try {
            response = objectMapper.readValue(rawResponse, ProviderResponse.class);
        } catch (RuntimeException exception) {
            return failure(null, "INVALID_RESPONSE_JSON");
        }
        if (response == null || !model.equals(response.model()) || response.answers() == null
                || !questions.keySet().equals(response.answers().keySet())) {
            return failure(response, "MISSING_OR_INVALID_QUESTIONS");
        }
        Map<String, JevChoiceAnswer> answers = new LinkedHashMap<>();
        Map<String, JevDecision> decisions = new LinkedHashMap<>();
        try {
            for (var entry : questions.entrySet()) {
                JsonNode node = response.answers().get(entry.getKey());
                if (!hasNumericScores(node, entry.getValue().criteria().keySet())) {
                    return failure(response, "INVALID_CHOICE_RESPONSE");
                }
                JevChoiceAnswer answer = objectMapper.treeToValue(node, JevChoiceAnswer.class);
                answer.validate(entry.getValue().criteria().keySet());
                answers.put(entry.getKey(), answer);
                if (!"topic_category".equals(entry.getKey())) {
                    decisions.put(entry.getKey(), answer.evaluate());
                }
            }
        } catch (RuntimeException exception) {
            return failure(response, "INVALID_CHOICE_RESPONSE");
        }
        JevDecision recommendation = JevDecision.APPROVE;
        if (decisions.containsValue(JevDecision.HUMAN_REVIEW)) {
            recommendation = JevDecision.HUMAN_REVIEW;
        }
        if (decisions.containsValue(JevDecision.REJECT)) {
            recommendation = JevDecision.REJECT;
        }
        String category = answers.containsKey("topic_category")
                ? answers.get("topic_category").choice()
                : null;
        return new JevEvaluation(
                answers,
                decisions,
                recommendation,
                category,
                readToken(response.usage(), "input_tokens"),
                readToken(response.usage(), "output_tokens"),
                null
        );
    }

    private JevEvaluation failure(ProviderResponse response, String error) {
        return new JevEvaluation(
                Map.of(),
                Map.of(),
                JevDecision.HUMAN_REVIEW,
                null,
                response == null ? null : readToken(response.usage(), "input_tokens"),
                response == null ? null : readToken(response.usage(), "output_tokens"),
                error
        );
    }

    private boolean hasNumericScores(JsonNode answer, Set<String> options) {
        if (answer == null || answer.get("probabilities") == null
                || !answer.get("probabilities").isObject()) {
            return false;
        }
        JsonNode confidence = answer.get("confidence");
        if (confidence != null && !confidence.isNull() && !confidence.isNumber()) {
            return false;
        }
        for (String option : options) {
            JsonNode probability = answer.get("probabilities").get(option);
            if (probability == null || !probability.isNumber()) {
                return false;
            }
        }
        return true;
    }

    private Long readToken(Map<String, JsonNode> usage, String key) {
        JsonNode value = usage == null ? null : usage.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0) {
            return null;
        }
        return value.longValue();
    }

    private record ProviderResponse(
            String model,
            Map<String, JsonNode> answers,
            Map<String, JsonNode> usage
    ) {
    }
}
