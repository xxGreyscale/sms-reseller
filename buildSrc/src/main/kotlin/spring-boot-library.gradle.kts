// buildSrc/src/main/kotlin/spring-boot-library.gradle.kts
// For shared-security, shared-observability — NOT executable JARs
plugins {
    id("java")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Disable bootJar — library modules do not have a main class
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar>().configureEach {
    enabled = false
}
tasks.withType<Jar>().configureEach {
    enabled = true
}
