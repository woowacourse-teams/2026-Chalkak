package com.chalkak.backend.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dtoValidationFailure() throws Exception {
        mockMvc.perform(post("/test/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"POST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("이름은 비어 있을 수 없습니다."));
    }

    @Test
    void typeMismatchFailureKeepsFieldName() throws Exception {
        mockMvc.perform(get("/test/count").queryParam("count", "한글"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("count: 요청 값의 형식이 올바르지 않습니다."));
    }

    @Test
    void invalidJsonValue() throws Exception {
        mockMvc.perform(post("/test/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"찰캌\",\"type\":\"POSST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("JSON 형식이 올바르지 않거나 요청 본문이 비어 있습니다."));
    }

    @Test
    void emptyRequestBody() throws Exception {
        mockMvc.perform(post("/test/requests")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("JSON 형식이 올바르지 않거나 요청 본문이 비어 있습니다."));
    }

    @Test
    void methodParameterValidationFailure() throws Exception {
        mockMvc.perform(get("/test/count").queryParam("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message", containsString("수량은 1 이상이어야 합니다.")));
    }

    @Test
    void apiNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("요청한 API를 찾을 수 없습니다."));
    }

    @Test
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("비즈니스 요청이 올바르지 않습니다."));
    }

    @Test
    void notFoundException() throws Exception {
        mockMvc.perform(get("/test/not-found-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("대상을 찾을 수 없습니다."));
    }

    @Test
    void forbiddenException() throws Exception {
        mockMvc.perform(get("/test/forbidden-error"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("관리자 API에 접근할 수 없습니다."));
    }

    @Test
    void unsupportedHttpMethod() throws Exception {
        mockMvc.perform(put("/test/business-error"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식이거나 형식입니다."));
    }

    @Test
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/requests")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("이름"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식이거나 형식입니다."));
    }

    @Test
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버에서 요청을 처리하지 못했습니다."));
    }

    @Test
    void serverSideStandardException() throws Exception {
        mockMvc.perform(get("/test/server-error-response"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버에서 요청을 처리하지 못했습니다."));
    }

    @RestController
    @RequestMapping("/test")
    public static class TestController {

        @PostMapping("/requests")
        void create(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/count")
        void count(@RequestParam @Min(value = 1, message = "수량은 1 이상이어야 합니다.") int count) {
        }

        @GetMapping("/business-error")
        void businessError() {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "비즈니스 요청이 올바르지 않습니다.");
        }

        @GetMapping("/not-found-error")
        void notFoundError() {
            throw new NotFoundException(ErrorCode.BUSINESS_ERROR, "대상을 찾을 수 없습니다.");
        }

        @GetMapping("/forbidden-error")
        void forbiddenError() {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "관리자 API에 접근할 수 없습니다.");
        }

        @GetMapping("/unexpected-error")
        void unexpectedError() {
            throw new RuntimeException("예상치 못한 예외");
        }

        @GetMapping("/server-error-response")
        void serverErrorResponse() {
            throw new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public record TestRequest(
            @NotBlank(message = "이름은 비어 있을 수 없습니다.") String name,
            TestType type
    ) {
    }

    public enum TestType {
        POST
    }
}
