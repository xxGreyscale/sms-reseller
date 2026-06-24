package com.smsreseller.payment;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ARCH-01: clean-architecture inward dependency rule for payment-service.
 *
 * Layer boundaries (target state after 07-03 package move):
 *   domain        → must not depend on application / infrastructure / presentation
 *   application   → may depend on domain only
 *   infrastructure → may depend on domain and application (implements ports)
 *   presentation  → may depend on domain and application (REST layer)
 *
 * Expected state in Wave 1 (this plan): RED.
 * Classes are still in package-by-feature layout; no domain/application/infrastructure/presentation
 * packages exist yet. The test will become GREEN after 07-03 moves classes into layers.
 *
 * NOTE: domain classes are permitted to carry JPA/jakarta.persistence annotations — this is a
 * deliberate MVP compromise (persistence-annotated domain model) documented in CLEAN-ARCHITECTURE.md.
 * A rule forbidding Spring/Jakarta annotations in domain is intentionally omitted.
 */
@AnalyzeClasses(packages = "com.smsreseller.payment", importOptions = ImportOption.DoNotIncludeTests.class)
public class PaymentArchitectureTest {

    @ArchTest
    public static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Presentation").definedBy("..presentation..")
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Presentation")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Presentation");

    @ArchTest
    public static final ArchRule domainPurity = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..presentation..");
}
