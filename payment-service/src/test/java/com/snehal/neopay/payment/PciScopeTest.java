package com.snehal.neopay.payment;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SAQ-A boundary: the account number never becomes a field in this service.
 *
 * <p>Card data enters through the vault, which returns a token. A PAN field appearing
 * anywhere in {@code payment-service} means the boundary has been crossed and this test
 * fails the build.
 */
@AnalyzeClasses(importOptions = ImportOption.DoNotIncludeTests.class,
        packages = "com.snehal.neopay.payment")
class PciScopeTest {

    @ArchTest
    static final ArchRule no_pan_field_in_payment_service =
            fields().that().areDeclaredInClassesThat()
                    .resideInAPackage("com.snehal.neopay.payment..")
                    .and().haveRawType(String.class)
                    .and().areNotStatic()
                    .should().haveNameNotMatching("(?i)^pan.*")
                    .because("card numbers stay in vault-service; only tokens cross the boundary")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_card_number_type_in_payment_service =
            noClasses().that().resideInAPackage("com.snehal.neopay.payment..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.snehal.neopay.vault.entity.Card")
                    .allowEmptyShould(true);
}
