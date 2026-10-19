package com.chalkak.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureConventionTest {

    static final List<ArchRule> RULES = List.of(
            classes().that().resideInAPackage("..api..dto.request..")
                    .and().haveSimpleNameEndingWith("Request")
                    .should().beRecords(),
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.data..", "software.amazon.awssdk.."),
            noClasses().that().resideInAPackage("com.chalkak.backend.*.repository..")
                    .should()
                    .beAssignableTo(org.springframework.data.jpa.repository.JpaRepository.class),
            classes().that()
                    .areAssignableTo(org.springframework.data.jpa.repository.JpaRepository.class)
                    .and().resideInAPackage("com.chalkak.backend..")
                    .should()
                    .resideInAPackage("com.chalkak.backend.*.infrastructure.persistence.."),
            classes().that()
                    .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().resideInAnyPackage(
                            "com.chalkak.backend.*.api.v*.controller",
                            "com.chalkak.backend.*.api.internal.v*.controller"));

    @Test
    @DisplayName("운영 코드의 요청 DTO와 계층 의존 규칙을 검사한다")
    void check_productionClasses_followArchitectureConventions() {
        // Given
        var productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.chalkak.backend");

        // When & Then
        RULES.forEach(rule -> rule.check(productionClasses));
    }
}
