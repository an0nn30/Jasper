package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class FileTypeCatalogTest {

    @Test
    void theBuiltInCatalogueParsesAndCoversTheSpecsList() {
        FileTypeCatalog catalog = FileTypeCatalog.builtIn();
        assertThat(catalog.extensions()).contains(".java", ".py", ".md", ".json", ".yaml", ".yml", ".txt")
                .doesNotContain(".html", ".htm");
        assertThat(catalog.macTypes(".java")).containsExactly("com.sun.java-source");
        assertThat(catalog.linuxTypes(".yaml")).isEqualTo(catalog.linuxTypes(".yml"));
        assertThat(catalog.macTypes(".nope")).isEmpty();
        assertThat(catalog.macImports()).allSatisfy(i -> assertThat(i.identifier()).startsWith("dev.jasper."));
    }

    @Test
    void aMalformedCatalogueNamesItsLine() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileTypeCatalog.parse("# c\n.java\tcom.sun.java-source\n"))
                .withMessageContaining("line 2");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileTypeCatalog.parse(".a\tx\ty\n.a\tx\ty\n"))
                .withMessageContaining("line 2");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileTypeCatalog.parse(".a\tx\ty\nimport\tdev.jasper.q\tpublic.text\tq\tQ\n"))
                .withMessageContaining("dev.jasper.q");
    }

    @Test
    void extensionsFollowTheRule() {
        for (String bad : new String[] {".Java", "." + "a".repeat(17), ".html", ".htm", "java", "."}) {
            assertThatIllegalArgumentException()
                    .as(bad)
                    .isThrownBy(() -> FileTypeCatalog.parse("# c\n" + bad + "\tx\ty\n"))
                    .withMessageContaining("line 2");
        }
        assertThat(FileTypeCatalog.parse("." + "a".repeat(16) + "\tx\ty\n").extensions()).hasSize(1);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileTypeCatalog.parse(".a\tx\ty\nimport\tx\tpublic.text\t.a\tQ\n"))
                .withMessageContaining("line 2");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileTypeCatalog.parse(".a\tx\ty\nimport\tx\tpublic.text\tA\tQ\n"))
                .withMessageContaining("line 2");
    }

    @Test
    void parseKeepsEmptyColumnsAndImports() {
        FileTypeCatalog catalog = FileTypeCatalog.parse(
                "# c\n.a\t\ttext/a\n.b\tdev.jasper.b\t\n.c++\tdev.jasper.b\ttext/c\nimport\tdev.jasper.b\tpublic.source-code\tb,c++\tB source\n");
        assertThat(catalog.extensions()).containsExactly(".a", ".b", ".c++");
        assertThat(catalog.macTypes(".a")).isEmpty();
        assertThat(catalog.linuxTypes(".a")).containsExactly("text/a");
        assertThat(catalog.macTypes(".b")).containsExactly("dev.jasper.b");
        assertThat(catalog.linuxTypes(".b")).isEmpty();
        assertThat(catalog.macImports()).singleElement().satisfies(i -> {
            assertThat(i.identifier()).isEqualTo("dev.jasper.b");
            assertThat(i.conformsTo()).isEqualTo("public.source-code");
            assertThat(i.extensions()).containsExactly("b", "c++");
            assertThat(i.description()).isEqualTo("B source");
        });
    }
}
