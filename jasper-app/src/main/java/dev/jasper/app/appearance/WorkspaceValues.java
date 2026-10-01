package dev.jasper.app.appearance;

import com.formdev.flatlaf.IntelliJTheme;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ColorUIResource;

/** Effective workspace values from private, never-installed FlatLaf defaults. */
final class WorkspaceValues {
    private WorkspaceValues() {}

    static GeneratedTheme resolve(ThemeDocument document, GeneratedTheme generated) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Workspace values require the EDT");
        if (!document.uiComplete().orElse(false)) return generated;
        try {
            var json = ThemeLoader.json(generated.name(), generated.dark(), generated.author(), generated.uiKeys(), generated.icons());
            var laf = IntelliJTheme.createLaf(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            laf.setExtraDefaults(ThemeManager.APP_DEFAULTS);
            var defaults = laf.getDefaults();
            ThemeLoader.explicitColors(generated.uiKeys()).forEach((key, color) -> {
                if (defaults.get(key) == null) defaults.put(key, new ColorUIResource(color));
            });
            ChromeKeys.derive(defaults, generated.terminal().background());
            return ThemeGenerator.withWorkspaceTokens(document,
                WorkspaceTokenKeys.resolve(generated.tokens(), key -> defaults.getColor(key)));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
