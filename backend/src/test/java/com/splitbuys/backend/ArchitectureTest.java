package com.splitbuys.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/** The architecture, written down as rules that fail the build when someone breaks them. */
@AnalyzeClasses(packages = "com.splitbuys.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String ROOT = "com.splitbuys.backend";

    @ArchTest
    static final ArchRule features_do_not_form_dependency_cycles =
            slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule group_does_not_know_about_the_ledger =
            noClasses().that().resideInAPackage(ROOT + ".group..").should().dependOnClassesThat().resideInAPackage(ROOT + ".ledger..")
                    .because("the ledger implements group.LedgerPort, not the other way round");

    @ArchTest
    static final ArchRule user_depends_on_no_other_feature =
            noClasses().that().resideInAPackage(ROOT + ".user..").should().dependOnClassesThat()
                    .resideInAnyPackage(ROOT + ".auth..", ROOT + ".group..", ROOT + ".ledger..");

    @ArchTest
    static final ArchRule split_logic_is_pure =
            classes().that().resideInAPackage(ROOT + ".split..").should().onlyDependOnClassesThat()
                    .resideInAnyPackage(ROOT + ".split..", "java..")
                    .because("the split maths must stay framework-free and trivially testable");

    @ArchTest
    static final ArchRule common_does_not_depend_on_features =
            noClasses().that().resideInAPackage(ROOT + ".common..").should().dependOnClassesThat()
                    .resideInAnyPackage(ROOT + ".user..", ROOT + ".auth..", ROOT + ".group..", ROOT + ".ledger..", ROOT + ".split..");

    @ArchTest
    static final ArchRule domain_errors_know_nothing_about_http =
            classes().that().resideInAPackage(ROOT + ".common.error..").should().onlyDependOnClassesThat()
                    .resideInAnyPackage(ROOT + ".common.error..", "java..");

    @ArchTest
    static final ArchRule controllers_go_through_services =
            noClasses().that().areAnnotatedWith(RestController.class).should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule services_do_not_use_http_types =
            noClasses().that().haveSimpleNameEndingWith("Service").should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.http..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule constructor_injection_only =
            fields().should().notBeAnnotatedWith(Autowired.class);

    @ArchTest
    static final ArchRule entities_hide_their_state =
            fields().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class).should().notBePublic();
}
