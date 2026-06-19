// Source: docs.gradle.org/current/userguide/multi_project_builds.html [CITED]
// buildSrc/src/main/kotlin/spring-boot-service.gradle.kts
plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")  // Required for Spring expression language
}

dependencies {
    // ORDER MATTERS: lombok must precede mapstruct-processor (CLAUDE.md hard rule)
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    compileOnly("org.projectlombok:lombok")
}

// Allow skeleton service modules (no source yet) to pass bootJar.
// bootJar requires a main class; in Phase 1 services are empty skeletons.
// We configure a placeholder mainClass so bootJar resolves without error.
// Phase 2+ will override this via spring.main.class in application.yml or
// by having Spring Boot auto-detect the @SpringBootApplication class.
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar>().configureEach {
    mainClass.convention("placeholder.MainClass")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
