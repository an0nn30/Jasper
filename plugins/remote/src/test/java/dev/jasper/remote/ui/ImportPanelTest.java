package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.*;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

class ImportPanelTest {
    @Test void existingRowsAreExplicitUpdatesAndErrorsBlockSelection() throws Exception {
        edt(() -> {
            var old = RemoteHost.create("old", "host", 22, "u", Auth.AGENT, "", Optional.empty());
            var rows = ConfigImport.plan(SshConfig.parse("Host fresh\n IdentityFile key\nHost old\nHost bad\n IdentityFile %x\n", i -> List.of()), List.of(old), Path.of("/tmp"), "u");
            var selected = new ArrayList<ConfigImport.Candidate>();
            var panel = new ImportPanel(new FakeKit("dev.jasper.remote"), rows, List.of("skipped"), selected::addAll, c -> {}, () -> {}, () -> {});
            var view = panel.view();
            assertThat(check(panel, 0).checked()).isTrue();
            assertThat(check(panel, 1).checked()).isFalse();
            assertThat(check(panel, 1).label()).contains("Update");
            assertThat(check(panel, 2).state().enabled()).isFalse();
            emit(view, new UiEvent.Toggled(ImportPanel.check(1), true));
            click(view, ImportPanel.IMPORT);
            assertThat(selected).extracting(ConfigImport.Candidate::name).containsExactly("fresh", "old");
            assertThat(texts(view)).contains("Not importable: skipped");
            panel.busy(); assertThat(button(view, ImportPanel.REFRESH).state().enabled()).isFalse();
            assertThat(check(panel, 0).state().enabled()).isFalse();
            assertThat(panel.status()).isEqualTo("Reviewing credentials in Vault…");
            panel.failed("retry"); assertThat(button(view, ImportPanel.REFRESH).state().enabled()).isTrue();
            assertThat(panel.status()).isEqualTo("retry");
            panel.completed("1 new, 1 updated SSH hosts saved using Vault credentials");
            assertThat(button(view, ImportPanel.IMPORT).state().enabled()).isFalse();
            assertThat(button(view, ImportPanel.CANCEL).label()).isEqualTo("Close");
        });
    }

    /** Baseline: no default button, so Enter never imports; Escape cancels; Choose credential names the row. */
    @Test void enterNeverImportsEscapeCancelsAndChoiceShowsTheCredential() throws Exception {
        edt(() -> {
            var rows = ConfigImport.plan(SshConfig.parse("Host prod\n User me\n", i -> List.of()), List.of(), Path.of("/tmp"), "u");
            var picked = new ArrayList<String>(); int[] cancelled = {0};
            var panel = new ImportPanel(new FakeKit("dev.jasper.remote"), rows, List.of(), selection -> fail("Enter must not import"),
                candidate -> picked.add(candidate.name()), () -> {}, () -> cancelled[0]++);
            var view = panel.view();
            assertThat(primaries(view)).isEmpty();
            assertThat(form(view).actions()).extracting(Nodes.Button::label).containsExactly("Refresh", "Cancel", "Import");
            assertThat(button(view, ImportPanel.choose(0)).label()).isEqualTo("Choose credential…");
            click(view, ImportPanel.choose(0));
            assertThat(picked).containsExactly("prod");
            panel.credential(rows.getFirst().id(), "Work login");
            assertThat(texts(view)).contains("Vault: Work login");
            press(view, form(view).cancelAction());
            assertThat(cancelled[0]).isEqualTo(1);
        });
    }

    @Test void anEmptyConfigSaysSoAndCannotImport() throws Exception {
        edt(() -> {
            var panel = new ImportPanel(new FakeKit("dev.jasper.remote"), List.of(), List.of(), s -> {}, c -> {}, () -> {}, () -> {});
            assertThat(texts(panel.view())).contains("No importable Host entries found");
            assertThat(button(panel.view(), ImportPanel.IMPORT).state().enabled()).isFalse();
        });
    }

    static Nodes.Toggle check(ImportPanel panel, int row) { return node(panel.view(), ImportPanel.check(row), Nodes.Toggle.class); }
}
