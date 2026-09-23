package dev.jasper.remote.ui;
import dev.jasper.remote.hosts.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ImportPanelTest {
    @Test void existingRowsAreExplicitUpdatesAndErrorsBlockSelection() {
        var old = RemoteHost.create("old", "host", 22, "u", Auth.AGENT, "", Optional.empty());
        var rows = ConfigImport.plan(SshConfig.parse("Host fresh\n IdentityFile key\nHost old\nHost bad\n IdentityFile %x\n", i -> List.of()), List.of(old), Path.of("/tmp"), "u");
        var selected = new ArrayList<ConfigImport.Candidate>();
        var panel = new ImportPanel(rows, List.of("skipped"), selected::addAll, c -> {}, () -> {}, () -> {});
        assertThat(panel.checks.get(0).isSelected()).isTrue();
        assertThat(panel.checks.get(1).isSelected()).isFalse();
        assertThat(panel.checks.get(1).getText()).contains("Update");
        assertThat(panel.checks.get(2).isEnabled()).isFalse();
        panel.checks.get(1).setSelected(true); panel.importButton.doClick();
        assertThat(selected).extracting(ConfigImport.Candidate::name).containsExactly("fresh", "old");
        panel.busy(); assertThat(panel.refresh.isEnabled()).isFalse();
        panel.failed("retry"); assertThat(panel.refresh.isEnabled()).isTrue();
    }
}
