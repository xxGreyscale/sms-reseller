// services/contact-service/build.gradle.kts
plugins {
    id("spring-boot-service")
}

dependencies {
    implementation(project(":libs:shared-security"))
    implementation(project(":libs:shared-observability"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)  // AFTER lombok
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)  // REQUIRED: Flyway 10 split — do not omit
    runtimeOnly(libs.postgresql.driver)
    // Phase 4 additions
    implementation(libs.commons.csv)
    implementation(libs.libphonenumber)

    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
