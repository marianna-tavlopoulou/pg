package com.marianna.gateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.marianna.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnPersistence = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("jakarta.persistence..", "..entity..", "..repository..", "..adapter..")
        .because("Domain must be framework-free");

    @ArchTest
    static final ArchRule servicesMustNotDependOnControllers = noClasses()
        .that().resideInAPackage("..service..")
        .should().dependOnClassesThat().resideInAPackage("..controller..")
        .because("Services must not depend on the web layer");

    @ArchTest
    static final ArchRule controllersMustNotTouchRepositoriesDirectly = noClasses()
        .that().resideInAPackage("..controller..")
        .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..adapter..")
        .because("Controllers access data only through services");
}
