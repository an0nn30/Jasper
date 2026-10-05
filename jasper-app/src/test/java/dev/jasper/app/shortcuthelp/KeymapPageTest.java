package dev.jasper.app.shortcuthelp;

import static dev.jasper.app.shortcuthelp.ShortcutTestSupport.set;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settingsui.PageContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class KeymapPageTest {
    private final Contributions contributions = new Contributions();
    private final Map<String, String> saved = new HashMap<>();
    private final List<Map<String, Optional<String>>> writes = new ArrayList<>();
    private CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
    private final int[] changes = {0};
    // The Keymap page never touches the core-setting draft.
    private final PageContext context = new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
        () -> changes[0]++, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);

    private KeymapPage page() {
        return new KeymapPage(new KeymapPage.Hooks(contributions, () -> Map.copyOf(saved), () -> Map.of("dev.test", "Test Plugin"),
            true, edits -> { writes.add(edits); return result; }), context);
    }

    @Test void setStagesWithoutWritingAndTheRowsShowTheStagedBinding() throws Exception {
        var page = new KeymapPage[1];
        edt(() -> {
            page[0] = page();
            page[0].component();
            set(page[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
            assertThat(writes).isEmpty();
            assertThat(page[0].modified()).isTrue();
            assertThat(changes[0]).as("the dialog hears about it").isPositive();
            assertThat(page[0].panel().tree.entryAt(1).stroke()).isEqualTo(KeyStroke.getKeyStroke("ctrl H"));
        });
        edt(() -> {
            assertThat(ShortcutTestSupport.editing(page[0])).as("the card closes after Set").isFalse();
            page[0].dispose();
        });
    }

    @Test void applyWritesEveryStagedEditOnceThenTheSavedBindingsTakeOver() throws Exception {
        var page = new KeymapPage[1];
        edt(() -> {
            page[0] = page();
            page[0].component();
            set(page[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
        });
        edt(() -> set(page[0], "Paste", KeyStroke.getKeyStroke("ctrl J")));
        var applied = new AtomicReference<CompletableFuture<Void>>();
        edt(() -> applied.set(page[0].apply()));
        edt(() -> { });
        applied.get().get(5, TimeUnit.SECONDS);
        assertThat(writes).containsExactly(Map.of("copy", Optional.of("ctrl+h"), "paste", Optional.of("ctrl+j")));
        edt(() -> {
            assertThat(page[0].modified()).isFalse();
            saved.put("copy", "ctrl+h");
            saved.put("paste", "ctrl+j");
            page[0].refresh();
            ShortcutKit.search(page[0].panel()).setText("Copy");
            assertThat(page[0].panel().tree.entryAt(1).stroke()).isEqualTo(KeyStroke.getKeyStroke("ctrl H"));
            page[0].dispose();
        });
    }

    @Test void aFailedApplyKeepsTheEditsStagedAndResetDropsThem() throws Exception {
        result = CompletableFuture.failedFuture(new IllegalArgumentException("Shortcuts changed on disk."));
        var page = new KeymapPage[1];
        edt(() -> {
            page[0] = page();
            page[0].component();
            set(page[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
        });
        var applied = new AtomicReference<CompletableFuture<Void>>();
        edt(() -> applied.set(page[0].apply()));
        edt(() -> { });
        assertThatThrownBy(() -> applied.get().get(5, TimeUnit.SECONDS)).hasMessageContaining("changed on disk");
        edt(() -> {
            assertThat(page[0].modified()).isTrue();
            page[0].reset();
            assertThat(page[0].modified()).isFalse();
            ShortcutKit.search(page[0].panel()).setText("Copy");
            assertThat(page[0].panel().tree.entryAt(1).stroke())
                .isEqualTo(KeyBindings.defaults(true).strokeFor("copy").orElseThrow());
            page[0].dispose();
        });
    }

    @Test void searchTermsHighlightAndContributedActionsFollowTheCatalog() throws Exception {
        edt(() -> {
            var page = page();
            assertThat(page.id()).isEqualTo("keymap");
            assertThat(page.helpAnchor()).isEqualTo("shortcuts");
            assertThat(page.searchTerms()).contains("Keymap", "Clipboard").anySatisfy(term -> assertThat(term).startsWith("Copy"));
            page.component();
            page.highlight("paste");
            assertThat(ShortcutKit.search(page.panel()).getText()).isEqualTo("paste");
            page.highlight("");
            assertThat(ShortcutKit.search(page.panel()).getText()).as("clearing Settings' search clears what it put there").isEmpty();
            var action = contributions.addAction("dev.test.hello", "Hello", null, List.of(), Optional.of("cmd+alt+h"), ignored -> { });
            ShortcutKit.search(page.panel()).setText("hello");
            assertThat(page.panel().tree.entryAt(1).group()).isEqualTo("Test Plugin");
            assertThat(page.searchTerms()).contains("Test Plugin");
            action.close();
            assertThat(page.panel().tree.rowCount()).isZero();
            page.dispose();
        });
    }
}
