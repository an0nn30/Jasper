package dev.jasper.app.plugins;

import dev.jasper.sdk.JasperSdk;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** The zips {@code pluginZips} builds are what a user installs from Manage Plugins; stage each one through the real installer. */
class PluginZipsTest {
    @TempDir Path userDirectory;

    @Test void everyInRepoPluginZipStagesWithItsDescriptorAndBundledLibraries() throws Exception {
        List<Path> zips;
        try (var files = Files.list(Path.of(System.getProperty("jasper.pluginZips")))) { zips = files.filter(file -> file.toString().endsWith(".zip")).toList(); }
        assertThat(zips).as("one zip per plugins/ directory").hasSize(5);
        var ids = new ArrayList<String>();
        for (Path zip : zips) {
            PluginInstaller.Staged staged = PluginInstaller.stage(zip, userDirectory, Version.parse(JasperSdk.VERSION));
            var descriptor = staged.candidate().descriptor();
            ids.add(descriptor.id());
            assertThat(zip.getFileName().toString()).isEqualTo(descriptor.id() + "-" + descriptor.version() + ".zip");
            List<String> jars = staged.candidate().jars().stream().map(jar -> jar.getFileName().toString()).toList();
            if (descriptor.id().equals("dev.jasper.snippets"))
                assertThat(jars).as("bundled libraries travel with the plugin").contains("tomlj-1.1.1.jar", "antlr4-runtime-4.11.1.jar");
            else if (descriptor.id().equals("dev.jasper.vault"))
                assertThat(jars).as("BouncyCastle travels with the vault").contains("bcprov-jdk18on-1.85.2.jar");
            else if (descriptor.id().equals("dev.jasper.remote"))
                assertThat(jars).anySatisfy(jar -> assertThat(jar).startsWith("sshd-core-"));
            else assertThat(jars).as(descriptor.id() + " bundles nothing").hasSize(1);
        }
        assertThat(ids).containsExactlyInAnyOrder("dev.jasper.sample", "dev.jasper.snippets", "dev.jasper.history", "dev.jasper.vault", "dev.jasper.remote");
    }
}
