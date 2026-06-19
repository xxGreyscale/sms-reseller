// Root build.gradle.kts — plugins declared but NOT applied globally
// Version is omitted here because buildSrc puts the plugin on the classpath
// with the version already pinned in buildSrc/build.gradle.kts.
plugins {
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
}

// Repositories visible to all subprojects
allprojects {
    repositories {
        mavenCentral()
    }
}
