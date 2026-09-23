package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.ConfigImport;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.hosts.SshConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImportPanelTest {
    static ConfigImport.Candidate candidate(String name, boolean exists, String... notes) {
        var entry = new SshConfig.Entry(name, Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        return new ConfigImport.Candidate(entry, RemoteHost.create(name, name, 22, "u", Auth.AGENT, "", Optional.empty()), exists, List.of(notes));
    }

    @Test void checksNewEntriesByDefaultAndImportsTheChecked() {
        List<RemoteHost> imported = new ArrayList<>();
        var panel = new ImportPanel(List.of(candidate("a", false), candidate("b", true, "key not in the vault: uses the agent")), List.of("Host *.internal (pattern)"), imported::addAll, () -> { });
        assertThat(panel.checks).hasSize(2);
        assertThat(panel.checks.get(0).isSelected()).isTrue();
        assertThat(panel.checks.get(1).isSelected()).as("exists: unchecked").isFalse();
        assertThat(panel.checks.get(1).getText()).contains("b", "exists", "key not in the vault");
        assertThat(panel.skipped.getText()).contains("*.internal");
        panel.importButton.doClick();
        assertThat(imported).extracting(RemoteHost::name).containsExactly("a");
    }
}
