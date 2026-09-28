package dev.jasper.editor.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsFileTest {
    @TempDir Path folder;

    @Test void addsTheKeyAfterItsCommentedExample() throws Exception {
        Path file = Files.writeString(folder.resolve("editor.toml"), "# Vim mode.\n# vim = false\n\n# tab_size = 4\n");
        SettingsFile.setBoolean(file, "vim", true);
        assertThat(Files.readString(file)).isEqualTo("# Vim mode.\n# vim = false\nvim = true\n\n# tab_size = 4\n");
        SettingsFile.setBoolean(file, "vim", false);
        assertThat(Files.readString(file)).isEqualTo("# Vim mode.\n# vim = false\nvim = false\n\n# tab_size = 4\n");
    }

    @Test void createsTheFileAndKeepsTheKeyAboveTables() throws Exception {
        Path missing = folder.resolve("new/editor.toml");
        SettingsFile.setBoolean(missing, "vim", true);
        assertThat(Files.readString(missing)).isEqualTo("vim = true\n");
        Path tables = Files.writeString(folder.resolve("t.toml"), "font_size = 14\n[extra]\nx = 1\n");
        SettingsFile.setBoolean(tables, "vim", true);
        assertThat(Files.readString(tables)).isEqualTo("font_size = 14\nvim = true\n[extra]\nx = 1\n");
        assertThat(Files.list(folder).filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
    }
}
