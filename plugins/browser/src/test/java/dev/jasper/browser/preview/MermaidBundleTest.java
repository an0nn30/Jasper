package dev.jasper.browser.preview;

import dev.jasper.browser.engine.PreviewScheme;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled Mermaid (plan 9's slice 1b): {@code package/dist/mermaid.min.js} of npm's {@code mermaid-11.17.2.tgz},
 * unmodified, which every preview's origin serves at {@code /mermaid.min.js}.
 */
class MermaidBundleTest {
    /** The SHA-256 of the tarball's {@code package/dist/mermaid.min.js}, 3,572,661 bytes. */
    static final String MERMAID_SHA256 = "581ed7d74bd9048d0e3a91363927d72ef22942d7722546b27f7cc29e35390eb8";
    private static final String RESOURCE = "/dev/jasper/browser/third_party/mermaid.min.js";

    @Test void theBundledFileIsTheTarballsUnmodified() throws IOException, NoSuchAlgorithmException {
        byte[] file = bundled();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file))).isEqualTo(MERMAID_SHA256);
        assertThat(file.length).as("between 1 and 8 MiB").isBetween(1 << 20, 8 << 20);
    }

    @Test void everyPreviewsOriginServesIt() throws IOException {
        var scheme = new PreviewScheme(Runnable::run);
        var mermaid = scheme.answer("GET", scheme.open().shell() + "mermaid.min.js").join();
        assertThat(mermaid.status()).isEqualTo(200);
        assertThat(mermaid.mimeType()).isEqualTo("text/javascript");
        assertThat(mermaid.body()).isEqualTo(bundled());
    }

    /**
     * Review Focus 1 and N5: the diagrams draw in Mermaid's strict security level, which {@code preview.js} sets and a
     * diagram's {@code %%{init}%%} or front matter cannot change. Mermaid sends each diagram's configuration through
     * its {@code sanitize}, which deletes {@code secure} and every key the site configuration's {@code secure} list
     * names; {@code preview.js} leaves that list at this default ({@code PreviewScriptTest.mermaidIsStrict}). A newer
     * Mermaid must keep both.
     */
    @Test void aDiagramsDirectivesCannotChangeItsSecurityLevel() throws IOException {
        String script = new String(bundled(), StandardCharsets.UTF_8);
        assertThat(script).as("the defaults").contains("securityLevel:\"strict\",startOnLoad:!0,arrowMarkerAbsolute:!1,"
            + "secure:[\"secure\",\"securityLevel\",\"startOnLoad\",\"maxTextSize\",\"suppressErrorRendering\",\"maxEdges\"]");
        assertThat(script).as("sanitize").contains("[\"secure\",...io.secure??[]].forEach(t=>{Object.hasOwn(e,t)"
            + "&&(se.debug(`Denied attempt to modify a secure key ${t}`,e[t]),delete e[t])})");
        assertThat(script).as("its one global, set last")
            .endsWith("globalThis[\"mermaid\"] = globalThis.__esbuild_esm_mermaid_nm[\"mermaid\"].default;\n");
    }

    private static byte[] bundled() throws IOException {
        try (InputStream in = MermaidBundleTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as(RESOURCE).isNotNull();
            return in.readAllBytes();
        }
    }
}
