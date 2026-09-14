package dev.jasper.app;

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
