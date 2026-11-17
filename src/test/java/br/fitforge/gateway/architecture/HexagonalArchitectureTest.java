package br.fitforge.gateway.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/** Testes de arquitetura: garantem a separação de camadas (Hexagonal / Onion) do Gateway & BFF. */
@AnalyzeClasses(packages = "br.fitforge.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule camadasRespeitamAArquiteturaHexagonal = onionArchitecture()
            .domainModels("br.fitforge.gateway.application.model..", "br.fitforge.gateway.application.exception..")
            .applicationServices("br.fitforge.gateway.application..")
            .adapter("web", "br.fitforge.gateway.adapter.in.web..")
            .adapter("http", "br.fitforge.gateway.adapter.out.http..")
            .withOptionalLayers(true);

    @ArchTest
    static final ArchRule nucleoNaoDependeDeSpring = noClasses().that().resideInAPackage("br.fitforge.gateway.application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .because("o núcleo (BffService, portas, modelo) não pode conhecer o framework");

    @ArchTest
    static final ArchRule nucleoNaoDependeDosAdapters = noClasses().that().resideInAPackage("br.fitforge.gateway.application..")
            .should().dependOnClassesThat().resideInAnyPackage("br.fitforge.gateway.adapter..", "br.fitforge.gateway.infrastructure..", "br.fitforge.gateway.config..");

    @ArchTest
    static final ArchRule controllerNaoAcessaClientesHttp = noClasses().that().resideInAPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
            .because("o controller só conversa com o caso de uso (porta de entrada)");

    @ArchTest
    static final ArchRule portasSaoInterfaces = classes().that().resideInAnyPackage("..application.port..")
            .should().beInterfaces();
}
