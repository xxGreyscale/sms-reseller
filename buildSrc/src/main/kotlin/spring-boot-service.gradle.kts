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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
