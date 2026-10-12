package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The bundled theme file is the colour reference's machine-readable block, so the two cannot drift. */
class SlateSteelFileTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();

    @Test void theBundledFileIsTheReferenceCopysJsonBlock() throws Exception {
        String reference = Files.readString(ROOT.resolve("docs/design/theme-engine/slate-steel.md"));
        var block = Pattern.compile("```json\\n(.*?)\\n```", Pattern.DOTALL).matcher(reference);
        assertThat(block.find()).as("the reference's machine-readable block").isTrue();
        String json = block.group(1);
        assertThat(block.find()).as("one machine-readable block").isFalse();
        String bundled;
        try (var in = SlateSteelFileTest.class.getResourceAsStream("/dev/jasper/ui/theme/slate-steel.json")) {
            assertThat(in).as("the bundled theme file").isNotNull();
            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(bundled.strip()).isEqualTo(json.strip());
        assertThat(json).contains("\"editor\"", "\"slate-steel-dark\"", "\"high-contrast\"");
    }
}
