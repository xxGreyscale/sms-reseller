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

// Import the Spring Boot BOM so BOM-managed dependencies resolve without explicit versions.
// The org.springframework.boot plugin is NOT applied (that would enable bootJar),
// but we still need the BOM for version management.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.9")
    }
}

// Disable bootJar — library modules do not have a main class
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar>().configureEach {
    enabled = false
}
tasks.withType<Jar>().configureEach {
    enabled = true
}

dependencies {
    // junit-platform-launcher must match junit-platform-engine from spring-boot-starter-test.
    // Without this, Gradle's built-in launcher and the BOM-managed engine diverge
    // ("OutputDirectoryProvider not available" error). Using BOM-managed version ensures alignment.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
