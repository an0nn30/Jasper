package dev.jasper.app;

import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WindowChromeHintTest {
    @TempDir Path directory;

    @Test void liveMacBindingChangesPaintEveryModifierAndNoneRemovesTheHint() throws Exception {
        Path file = directory.resolve("config.toml");
        var service = new ConfigService(file, true);
        ConfigurationController[] controller = new ConfigurationController[1];
        WindowContent[] owner = new WindowContent[1];
        JButton[] button = new JButton[1];
        try {
            edt(() -> {
                var themes = new ThemeController();
                controller[0] = new ConfigurationController(themes, service);
                owner[0] = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {}, themes);
                controller[0].register(owner[0]);
                button[0] = (JButton) owner[0].toolbar().getComponent(0);
            });
            String[][] examples = {
                {"cmd+t", "\u2318T"},
                {"cmd+shift+t", "\u21e7\u2318T"},
                {"cmd+alt+t", "\u2325\u2318T"},
                {"ctrl+alt+shift+cmd+t", "\u2303\u2325\u21e7\u2318T"},
                {"F12", "F12"},
                {"none", ""}
            };
            for (String[] example : examples) {
                Files.writeString(file, "[keybindings]\nnew_tab='" + example[0] + "'\n");
                service.reload().get();
                edt(() -> {
                    assertThat(owner[0].toolbar().getComponent(0)).isSameAs(button[0]);
                    var actual = new BufferedImage(1200, 120, BufferedImage.TYPE_INT_RGB);
                    button[0].setSize(300, 30);
                    var graphics = actual.createGraphics(); graphics.scale(4, 4); button[0].paint(graphics); graphics.dispose();
                    var expected = new BufferedImage(1200, 120, BufferedImage.TYPE_INT_RGB);
                    graphics = expected.createGraphics(); graphics.scale(4, 4);
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    graphics.setColor(UIManager.getColor("Jasper.mutedForeground"));
                    graphics.setFont(button[0].getFont().deriveFont(UIScale.scale(10f)));
                    graphics.drawString(example[1], 0, 20); graphics.dispose();
                    assertThat(hintInk(actual)).as("painted shortcut for %s", example[0]).isEqualTo(hintInk(expected));
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

    /** Compare the actual hint's glyph ink, independent of its position within the toolbar button. */
    private static List<String> hintInk(BufferedImage image) {
        int color = UIManager.getColor("Jasper.mutedForeground").getRGB();
        int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (image.getRGB(x, y) == color) {
                left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
        }
        var rows = new ArrayList<String>();
        for (int y = top; y <= bottom; y++) {
            var row = new StringBuilder();
            for (int x = left; x <= right; x++) row.append(image.getRGB(x, y) == color ? '#' : '.');
            rows.add(row.toString());
        }
        return rows;
    }
}
