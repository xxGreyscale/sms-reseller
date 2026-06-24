// Source: docs.gradle.org/current/userguide/multi_project_builds.html [CITED]
rootProject.name = "sms-reseller"

include(
    "services:identity-service",
    "services:catalog-service",
    "services:wallet-service",
    "services:payment-service",
    "services:contact-service",
    "services:messaging-service",
    "services:notification-service",
    "services:admin-service",
    "libs:shared-security",
    "libs:shared-observability",
    "apps:admin-web"
)
