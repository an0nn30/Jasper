package dev.jasper.app.testsupport;

import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.presentation.KitParser;
import dev.jasper.app.presentation.PresentationFacts;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.JasperTheme;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The Enterprise plugin's resources as the sources hold them, read the way the host reads a jar (bounded, inside the
 * root): its kit (with its IBM Plex files registered in this JVM, as the application registers them), chrome and theme.
 */
public final class EnterpriseResources {
    /** The repository, from the test task's {@code jasper.repoRoot}. */
    public static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    /** The plugin's resources folder: the root of its jar. */
    public static final Path RESOURCES = ROOT.resolve("plugins/enterprise/src/main/resources");

    private EnterpriseResources() { }

    /** Reads a jar path under {@link #RESOURCES}: empty when absent or outside, else at most {@code maxBytes + 1} bytes. */
    public static KitParser.Files files() {
        return (path, maxBytes) -> {
            Path file = RESOURCES.resolve(path).normalize();
            if (!file.startsWith(RESOURCES) || !Files.isRegularFile(file)) return Optional.empty();
            try (var in = Files.newInputStream(file)) { return Optional.of(in.readNBytes(maxBytes + 1)); }
            catch (IOException unreadable) { return Optional.empty(); }
        };
    }

    /** The shipped kit's facts, read by the production code. */
    public static PresentationFacts facts() {
        return PresentationFacts.read(new PresentationFacts.Source("dev.jasper.enterprise", "presentation/enterprise.kit.toml", files(),
            List.of("themes/enterprise.json")));
    }

    /** The shipped kit, parsed by the production parser, after its IBM Plex files are registered in this JVM. */
    public static Kit kit() {
        var parsed = facts().parsed().orElseThrow();
        for (FontFile file : parsed.faces().files()) {
            // A false return means a copy is registered already, which draws by the same name.
            try { GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.bytes()))); }
            catch (FontFormatException | IOException failure) { throw new AssertionError(file.path() + " is not a font", failure); }
        }
        return parsed.kit();
    }

    /** The shipped kit file's chrome points. */
    public static ChromeSpec chrome() { return facts().parsed().orElseThrow().chrome(); }

    private static JasperTheme theme;

    /** The shipped Enterprise theme, parsed once by the production loader. */
    public static synchronized JasperTheme theme() {
        if (theme != null) return theme;
        try { theme = JasperTheme.parse(Files.readString(RESOURCES.resolve("themes/enterprise.json")), "enterprise"); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        return theme;
    }
}
