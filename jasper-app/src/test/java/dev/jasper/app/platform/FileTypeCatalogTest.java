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
}
