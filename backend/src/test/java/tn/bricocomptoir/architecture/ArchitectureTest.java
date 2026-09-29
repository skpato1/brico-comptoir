package tn.bricocomptoir.architecture;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {
    private static final String ROOT = "tn.bricocomptoir";
    private static final List<String> MODULES = List.of("identity", "catalog", "media", "inventory", "packs", "sales", "content", "notifications", "privacy");
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "privacy",Set.of("identity","sales","notifications"),
            "identity", Set.of("notifications"), "notifications",Set.of(), "catalog", Set.of(), "content", Set.of(), "media", Set.of("catalog", "packs"),
            "inventory", Set.of("catalog"), "packs", Set.of("catalog", "inventory"),
            "sales", Set.of("catalog", "inventory", "packs", "identity", "notifications"));
    private final JavaClasses production = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

    @Test
    void domainAndApplicationRemainPureJava() {
        assertThat(production).isNotEmpty();
        checkPurity(production, ROOT);
    }

    private static void checkPurity(JavaClasses imported, String root) {
        for (String module : MODULES) {
            String base = root + "." + module;
            // Empty layers are intentional until the corresponding business step is implemented.
            classes().that().resideInAPackage(base + ".domain..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("java..", base + ".domain..")
                    .allowEmptyShould(true).check(imported);
            classes().that().resideInAPackage(base + ".application..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", base + ".domain..", base + ".application..")
                    .allowEmptyShould(true).check(imported);
        }
    }

    @Test
    void crossModuleDependenciesOnlyUseAuthorizedPublicPortsFromModuleAdapters() {
        for (var origin : production) {
            for (String source : MODULES) {
                if (!isInPackage(origin.getPackageName(), ROOT + "." + source)) continue;
                for (var dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    for (String target : MODULES) {
                        if (source.equals(target) || !isInPackage(targetPackage, ROOT + "." + target)) continue;
                        assertThat(ALLOWED.get(source)).as(dependency.getDescription()).contains(target);
                        assertThat(isInPackage(origin.getPackageName(), ROOT + "." + source + ".adapter.out.module"))
                                .as(dependency.getDescription()).isTrue();
                        assertThat(isInPackage(targetPackage, ROOT + "." + target + ".application.port.in"))
                                .as(dependency.getDescription()).isTrue();
                    }
                }
            }
        }
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(production);
        noClasses().that().resideInAnyPackage(MODULES.stream().map(module -> ROOT + "." + module + "..")
                        .toArray(String[]::new))
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".bootstrap..")
                .allowEmptyShould(true).check(production);
    }

    private static boolean isInPackage(String actual, String expected) {
        return actual.equals(expected) || actual.startsWith(expected + ".");
    }

    @Test
    void purityRuleDetectsAFrameworkImportInADomain() {
        var invalid = new ClassFileImporter().importClasses(fixtures.catalog.domain.InvalidDomain.class);
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
                () -> checkPurity(invalid, "fixtures"));
    }
}
