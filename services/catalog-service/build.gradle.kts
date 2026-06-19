// services/catalog-service/build.gradle.kts
plugins {
    id("spring-boot-service")
}

dependencies {
    implementation(project(":libs:shared-security"))
    implementation(project(":libs:shared-observability"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)  // REQUIRED: Flyway 10 split — do not omit
    // Service-specific deps added here in later phases
}
