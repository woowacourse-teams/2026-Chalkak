package com.chalkak.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArchitectureRulesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("올바른 계층과 record 요청을 허용한다")
    void evaluate_validFixture_passesEveryRule() throws IOException {
        // Given
        var fixture = compileFixture(-1);

        // When & Then
        ArchitectureConventionTest.RULES.forEach(rule -> rule.check(fixture));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @DisplayName("각 규칙에 위반을 주입하면 실제 위반으로 판정한다")
    void evaluate_invalidFixture_reportsViolation(int ruleIndex) throws IOException {
        // Given
        var fixture = compileFixture(ruleIndex);

        // When
        var result = ArchitectureConventionTest.RULES.get(ruleIndex).evaluate(fixture);

        // Then
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("Bad"));
    }

    private com.tngtech.archunit.core.domain.JavaClasses compileFixture(int invalidRule)
            throws IOException {
        // Compile isolated bytecode; these synthetic classes never enter the
        // application classpath.
        var sources = new ArrayList<>(List.of(
                new Source("org.springframework.data.jpa.repository", "JpaRepository",
                        "public interface JpaRepository {}"),
                new Source("software.amazon.awssdk.services.s3", "S3Client",
                        "public interface S3Client {}"),
                new Source("org.springframework.web.bind.annotation", "RestController",
                        "public @interface RestController {}"),
                new Source("com.chalkak.backend.sample.api.v1.dto.request", "GoodRequest",
                        "public record GoodRequest(String name) {}"),
                new Source("com.chalkak.backend.sample.service", "GoodService",
                        "public class GoodService {}"),
                new Source("com.chalkak.backend.sample.repository", "GoodRepository",
                        "public interface GoodRepository {}"),
                new Source("com.chalkak.backend.sample.infrastructure.persistence",
                        "GoodJpaRepository",
                        "public interface GoodJpaRepository extends org.springframework.data.jpa.repository.JpaRepository {}"),
                new Source("com.chalkak.backend.sample.api.internal.v1.controller",
                        "GoodController",
                        "@org.springframework.web.bind.annotation.RestController public class GoodController {}")));
        var invalidSources = List.of(
                new Source("com.chalkak.backend.sample.api.v1.dto.request", "BadRequest",
                        "public class BadRequest {}"),
                new Source("com.chalkak.backend.sample.service", "BadService",
                        "public class BadService { software.amazon.awssdk.services.s3.S3Client client; }"),
                new Source("com.chalkak.backend.sample.repository", "BadRepository",
                        "public interface BadRepository extends org.springframework.data.jpa.repository.JpaRepository {}"),
                new Source("com.chalkak.backend.sample.domain", "BadJpaRepository",
                        "public interface BadJpaRepository extends org.springframework.data.jpa.repository.JpaRepository {}"),
                new Source("com.chalkak.backend.sample.service", "BadController",
                        "@org.springframework.web.bind.annotation.RestController public class BadController {}"));
        if (invalidRule >= 0) {
            sources.add(invalidSources.get(invalidRule));
        }
        Path output = Files.createDirectories(temporaryDirectory.resolve("classes"));
        var arguments = new ArrayList<>(List.of("-d", output.toString()));
        for (Source source : sources) {
            Path file = temporaryDirectory.resolve(source.packageName().replace('.', '/'))
                    .resolve(source.className() + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "package " + source.packageName() + ";\n" + source.body());
            arguments.add(file.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                arguments.toArray(String[]::new))).isZero();
        return new ClassFileImporter().importPath(output);
    }

    private record Source(String packageName, String className, String body) {
    }
}
