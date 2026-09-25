package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WindowChromeHintTest {
    @TempDir Path directory;

    @Test void liveMacBindingChangesReachTheToolbarTooltipAndNoneRemovesTheShortcut() throws Exception {
        Path file = directory.resolve("config.toml");
        var service = new ConfigService(file, true);
        ConfigurationTestSupport[] controller = new ConfigurationTestSupport[1];
        WindowContent[] owner = new WindowContent[1];
        JButton[] button = new JButton[1];
        try {
            edt(() -> {
                var themes = new ThemeController();
                controller[0] = new ConfigurationTestSupport(themes, service);
                owner[0] = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {}, themes);
                controller[0].register(owner[0]);
                button[0] = (JButton) owner[0].toolbar().getComponent(0);
            });
            String[][] examples = {
                {"cmd+t", "New Tab (\u2318T)"},
                {"cmd+shift+t", "New Tab (\u21e7\u2318T)"},
                {"cmd+alt+t", "New Tab (\u2325\u2318T)"},
                {"ctrl+alt+shift+cmd+t", "New Tab (\u2303\u2325\u21e7\u2318T)"},
                {"F12", "New Tab (F12)"},
                {"none", "New Tab"}
            };
            for (String[] example : examples) {
                Files.writeString(file, "[keybindings]\nnew_tab='" + example[0] + "'\n");
                service.reload().get();
                edt(() -> {
                    assertThat(owner[0].toolbar().getComponent(0)).isSameAs(button[0]);
                    assertThat(button[0].getToolTipText()).as("tooltip for %s", example[0]).isEqualTo(example[1]);
                });
            }
        } finally {
            edt(() -> { if (owner[0] != null) owner[0].close(); if (controller[0] != null) controller[0].close(); });
            service.close();
        }
    }

    @Test void loadedStatusDoesNotExposeTheObsoleteNotLoadedTooltip() throws Exception {
        edt(() -> {
            try (var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {})) {
                owner.setConfigurationState(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), directory.resolve("config.toml"), true));
                assertThat(owner.status().getToolTipText()).isNull();
                assertThat(owner.status().configButton().getToolTipText()).isEqualTo(directory.resolve("config.toml").toString());
            }
        });
    }
}
