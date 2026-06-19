// libs/shared-security/build.gradle.kts
plugins {
    id("spring-boot-library")
}

dependencies {
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    // nimbus-jose-jwt is pulled transitively by oauth2-resource-server — do not add directly
    testImplementation(libs.spring.boot.starter.test)
}
