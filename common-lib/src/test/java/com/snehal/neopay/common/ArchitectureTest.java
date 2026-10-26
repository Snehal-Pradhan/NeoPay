package com.snehal.neopay.common;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(importOptions = ImportOption.DoNotIncludeTests.class, packages = "com.snehal.neopay.common")
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_package_cycles =
            slices().matching("com.snehal.neopay.common.(*)..").should().beFreeOfCycles()
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repositories_must_not_depend_on_services =
            noClasses().that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAPackage("..service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entities_must_not_depend_on_web_or_repositories =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..controller..", "..repository..", "org.springframework.web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule services_must_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_field_injection =
            noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule common_lib_must_not_depend_on_services =
            noClasses().that().resideInAPackage("com.snehal.neopay.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.snehal.neopay.merchant..",
                            "com.snehal.neopay.payment..",
                            "com.snehal.neopay.vault..",
                            "com.snehal.neopay.operations..",
                            "com.snehal.neopay.gateway..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule methods_have_at_most_five_parameters =
            methods().should(new ArchCondition<JavaMethod>("have at most 5 parameters") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    int count = method.getRawParameterTypes().size();
                    if (count > 5) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " has " + count + " parameters"));
                    }
                }
            }).allowEmptyShould(true);
}
