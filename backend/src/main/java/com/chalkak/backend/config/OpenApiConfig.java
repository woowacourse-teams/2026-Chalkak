package com.chalkak.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import java.util.Set;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@OpenAPIDefinition(
        info = @Info(
                title = "Chalkak API",
                version = "v1",
                description = "Chalkak 사용자, 운영자 및 내부 API 문서"
        )
)
@SecurityScheme(
        name = "accessToken",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "소셜 로그인 또는 회원가입 응답으로 받은 액세스 토큰"
)
@SecurityScheme(
        name = "adminAccessToken",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "관리자 로그인 응답으로 받은 관리자 액세스 토큰"
)
@Configuration(proxyBeanMethods = false)
@Profile("!prod")
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("user-api")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/admin/**")
                .addOpenApiCustomizer(this::customizeOptionalAuthOnPostList)
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-api")
                .pathsToMatch("/api/v1/admin/**")
                .addOpenApiCustomizer(this::customizeAdminSecurity)
                .addOpenApiCustomizer(this::customizeAdminPostNullableSchemas)
                .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal-api")
                .pathsToMatch("/internal/v1/**")
                .build();
    }

    /**
     * springdoc 3.1은 nullable한 참조 타입을 {@code type: null}과 {@code $ref}의 교집합으로
     * 생성하므로, 실제 응답처럼 업로드 객체 또는 null을 허용하는 합집합 스키마로 교정한다.
     */
    private void customizeAdminPostNullableSchemas(OpenAPI openApi) {
        Schema<?> detailSchema = openApi.getComponents()
                .getSchemas()
                .get("AdminPostDetailResponse");
        if (detailSchema == null) {
            return;
        }

        ComposedSchema nullableImageUploadSchema = new ComposedSchema();
        nullableImageUploadSchema.addOneOfItem(
                new Schema<>().$ref("#/components/schemas/AdminPostDetailImageUpload")
        );
        nullableImageUploadSchema.addOneOfItem(new Schema<>().types(Set.of("null")));
        detailSchema.addProperty("imageUpload", nullableImageUploadSchema);
    }

    /**
     * 게시물 목록 조회는 토큰이 없어도 호출 가능하지만 있으면 isLiked가 개인화되는
     * 선택적 인증이다. swagger-core의 {@code @SecurityRequirement}는 이름이 빈
     * 요구사항을 표현하지 못해 익명 호출 허용을 애노테이션만으로 선언할 수 없으므로,
     * 생성된 문서에 빈 SecurityRequirement를 직접 추가해 "인증 없이 호출 가능"과
     * "accessToken으로 호출 가능"을 모두 문서화한다.
     */
    private void customizeOptionalAuthOnPostList(OpenAPI openApi) {
        PathItem postListPath = openApi.getPaths().get("/api/v1/posts");
        if (postListPath == null) {
            return;
        }

        Operation getPosts = postListPath.getGet();
        if (getPosts == null) {
            return;
        }

        List<SecurityRequirement> security = getPosts.getSecurity();
        if (security == null) {
            return;
        }

        security.addFirst(new SecurityRequirement());
    }

    private void customizeAdminSecurity(OpenAPI openApi) {
        openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations()
                .forEach(operation -> operation.setSecurity(List.of(
                        new SecurityRequirement().addList("adminAccessToken")
                ))));

        PathItem loginPath = openApi.getPaths().get("/api/v1/admin/auth/login");
        if (loginPath != null && loginPath.getPost() != null) {
            loginPath.getPost().setSecurity(List.of());
        }
    }
}
