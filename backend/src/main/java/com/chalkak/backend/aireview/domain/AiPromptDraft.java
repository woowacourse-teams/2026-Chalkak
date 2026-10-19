package com.chalkak.backend.aireview.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.io.JsonStringEncoder;

/**
 * 저장 전에 미리 실행해 볼 프롬프트 초안. 사용자 프롬프트 템플릿의 {@code {{변수}}}는 용도가 허용한 것만 쓸 수 있다.
 */
public record AiPromptDraft(
        AiPromptType type,
        String systemPrompt,
        String userPromptTemplate,
        String model,
        AiPromptParameters parameters
) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)}}");

    public AiPromptDraft {
        if (type == null || parameters == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "프롬프트 유형과 옵션이 필요합니다.");
        }
        validateNotBlank(systemPrompt, "시스템 프롬프트를 입력해 주세요.");
        validateNotBlank(userPromptTemplate, "사용자 프롬프트 템플릿을 입력해 주세요.");
        validateNotBlank(model, "모델을 입력해 주세요.");
        validateNoPlaceholder(systemPrompt);
        validatePlaceholders(type, userPromptTemplate);
    }

    /**
     * 주제 제목으로 템플릿의 변수를 치환한다. 변수 이름은 용도(type)가 정하므로 호출하는 쪽은 값만 넘긴다.
     */
    public String renderUserPrompt(String topicTitle) {
        if (topicTitle == null) {
            throw new IllegalArgumentException("주제 제목이 필요합니다.");
        }
        return render(Map.of(type.topicTitleVariable(), topicTitle));
    }

    /**
     * 템플릿의 모든 변수를 값으로 한 번에 치환한다. 값에 변수 표기가 들어 있어도 다시 치환하지 않는다.
     */
    private String render(Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(userPromptTemplate);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException("프롬프트 변수 값이 없습니다.");
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(escapeJsonString(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 템플릿이 JSON 문자열 안에 값을 넣는 경우에도 유효한 JSON이 되도록 따옴표·역슬래시·제어 문자를 이스케이프한다.
     */
    private static String escapeJsonString(String value) {
        StringBuilder escaped = new StringBuilder();
        JsonStringEncoder.getInstance().quoteAsString(value, escaped);
        return escaped.toString();
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private static void validateNoPlaceholder(String systemPrompt) {
        if (PLACEHOLDER.matcher(systemPrompt).find()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "시스템 프롬프트에는 변수를 쓸 수 없습니다.");
        }
    }

    private static void validatePlaceholders(AiPromptType type, String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            if (!type.allowedVariables().contains(matcher.group(1))) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "사용할 수 없는 프롬프트 변수가 있습니다. 사용 가능: " + type.allowedVariables());
            }
        }
    }
}
