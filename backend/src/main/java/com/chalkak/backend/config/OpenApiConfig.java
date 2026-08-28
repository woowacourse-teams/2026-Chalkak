package com.chalkak.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
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
        name = "userIdHeader",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-User-Id",
        description = "로컬·개발 환경에서 로그인 사용자를 식별하는 임시 헤더"
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
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-api")
                .pathsToMatch("/api/v1/admin/**")
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

        addNullableReference(
                detailSchema,
                "imageUpload",
                "AdminPostDetailImageUpload"
        );
        addNullableReference(
                detailSchema,
                "mediaDeletion",
                "AdminPostDetailMediaDeletion"
        );
    }

    private void addNullableReference(
            Schema<?> parentSchema,
            String propertyName,
            String referencedSchemaName
    ) {
        ComposedSchema nullableReferenceSchema = new ComposedSchema();
        nullableReferenceSchema.addOneOfItem(
                new Schema<>().$ref("#/components/schemas/" + referencedSchemaName)
        );
        nullableReferenceSchema.addOneOfItem(new Schema<>().types(Set.of("null")));
        parentSchema.addProperty(propertyName, nullableReferenceSchema);
    }
}
