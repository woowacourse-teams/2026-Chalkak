plugins {
    java
    checkstyle
    id("com.diffplug.spotless") version "8.10.2"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.chalkak"
version = "0.0.1-SNAPSHOT"
description = "backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-crypto")

    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation(platform("software.amazon.awssdk:bom:2.54.1"))
    implementation("software.amazon.awssdk:s3")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("com.tngtech.archunit:archunit:1.5.0")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        // Shared trunk ancestor survives squash merges; commits cannot reset this baseline.
        ratchetFrom("50e3764ec85d26714710bb86086edcf493768f26")
        eclipse("4.37").configFile("config/formatter/eclipse-java.xml")
        removeUnusedImports()
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "14.1.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    // These are operating-code conventions; test methods have a different naming policy.
    sourceSets = listOf(project.sourceSets.main.get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val architectureTest = tasks.register<Test>("architectureTest") {
    group = "verification"
    description = "Checks production architecture without starting Spring or PostgreSQL."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/architecture/**")
}

tasks.register("checkConventions") {
    group = "verification"
    description = "Checks formatting, source conventions and architecture."
    dependsOn("spotlessCheck", "checkstyleMain", architectureTest)
}

val testEnvContract = tasks.register<Exec>("testEnvContract") {
    group = "verification"
    description = "Tests environment contract checks with synthetic fixtures."
    workingDir(projectDir)
    commandLine("bash", "deploy/scripts/check_env_contract_test.sh")
}

val checkEnvContract = tasks.register<Exec>("checkEnvContract") {
    dependsOn(testEnvContract)
    group = "verification"
    description = "Checks environment variable contracts without exposing values."
    workingDir(projectDir)
    commandLine("bash", "deploy/scripts/check_env_contract.sh")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
}

tasks.named<Test>("test") {
    dependsOn(checkEnvContract)
}

tasks.named("check") {
    dependsOn(checkEnvContract)
}
