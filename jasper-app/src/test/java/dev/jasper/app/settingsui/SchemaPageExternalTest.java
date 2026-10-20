package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.kit;
import static dev.jasper.app.settingsui.PageKit.node;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.Nodes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class SchemaPageExternalTest {
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    private final PageContext context = PageKit.context(draft, id -> { }, () -> { });
    private final int[] stored = {2048};
    private final int[] restarts = {0};
    private final List<Integer> saves = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private CompletableFuture<Void> saveResult = CompletableFuture.completedFuture(null);
    private Optional<SchemaPage.Notice> notice = Optional.empty();
    private boolean enabled = true;

    private SchemaPage page() {
        return SchemaPage.page("system", "System Settings")
            .section("Java Virtual Machine")
            .external(new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", List.of("memory", "xmx"),
                new Nodes.Numeric(512, 1_048_576, 256, 0), () -> stored[0], () -> 2048,
                value -> value < 512 ? "Use 512 MiB or more." : "", value -> "Showing " + value + " MiB.",
                value -> {
                    saves.add(value);
                    if (!saveResult.isCompletedExceptionally()) stored[0] = value;
                    return saveResult;
                }, () -> enabled))
            .notice("vm.restart", () -> notice, new SchemaPage.Button("Restart Now", () -> restarts[0]++, () -> true))
            .refreshOn(listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); })
            .build(context);
    }

    private static String description(SchemaPage page, String id) { return ((Nodes.SettingRow) node(page, id)).description(); }

    @Test void anExternalRowShowsItsSavedValueAndHoldsAnEdit() {
        var page = page();
        var field = kit(page, "vm.heap.input", JTextField.class);
        assertThat(field.getText()).isEqualTo("2048");
        assertThat(description(page, "row:vm.heap")).isEqualTo("Showing 2048 MiB.");
        field.setText("4096");
        assertThat(page.modified()).isTrue();
        assertThat(page.externalModified()).isTrue();
        assertThat(page.externals().getFirst().pending()).isEqualTo(4096);
        assertThat(description(page, "row:vm.heap")).as("the comment follows the value").isEqualTo("Showing 4096 MiB.");
        assertThat(saves).isEmpty();
        field.setText("2048");
        assertThat(page.modified()).isFalse();
    }

    @Test void belowTheMinimumOrNoNumberIsAnErrorThatBlocksApply() {
        var page = page();
        var field = kit(page, "vm.heap.input", JTextField.class);
        field.setText("256");
        assertThat(page.valid()).isFalse();
        assertThat(page.externals().getFirst().error()).isEqualTo("Use 512 MiB or more.");
        assertThat(kit(page, "vm.heap.error", JLabel.class).getText()).isEqualTo("Use 512 MiB or more.");
        field.setText("");
        assertThat(page.externals().getFirst().error()).isEqualTo("Not a valid value.");
        field.setText("1024");
        assertThat(page.valid()).isTrue();
        assertThat(page.externals().getFirst().pending()).isEqualTo(1024);
    }

    @Test void resetOffersTheDefaultAndPageResetDiscards() {
        stored[0] = 4096;
        var page = page();
        var field = kit(page, "vm.heap.input", JTextField.class);
        assertThat(field.getText()).isEqualTo("4096");
        kit(page, "vm.heap:reset", JButton.class).doClick();
        assertThat(field.getText()).isEqualTo("2048");
        assertThat(page.externals().getFirst().pending()).isEqualTo(2048);
        page.reset();
        assertThat(field.getText()).isEqualTo("4096");
        assertThat(page.modified()).isFalse();
    }

    @Test void applySavesTheEditAndThenShowsTheSavedValue() {
        var page = page();
        kit(page, "vm.heap.input", JTextField.class).setText("4096");
        assertThat(page.apply()).isCompleted();
        assertThat(saves).containsExactly(4096);
        assertThat(page.modified()).isFalse();
        assertThat(kit(page, "vm.heap.input", JTextField.class).getText()).isEqualTo("4096");
    }

    @Test void aFailedSaveKeepsTheEdit() {
        saveResult = CompletableFuture.failedFuture(new IOException("Could not save jasper.vmoptions: Permission denied"));
        var page = page();
        kit(page, "vm.heap.input", JTextField.class).setText("4096");
        assertThat(page.apply()).isCompletedExceptionally();
        assertThat(page.modified()).isTrue();
        assertThat(kit(page, "vm.heap.input", JTextField.class).getText()).isEqualTo("4096");
        assertThat(page.externals().getFirst().error()).isEqualTo("Could not save jasper.vmoptions: Permission denied");
        assertThat(page.valid()).as("a failed save is no invalid value: Apply can retry").isTrue();
    }

    @Test void aDisabledExternalRowShowsButCannotBeEdited() {
        enabled = false;
        var page = page();
        assertThat(((Nodes.Field) node(page, "vm.heap")).state().enabled()).isFalse();
    }

    @Test void aNoticeShowsWhilePresentAndRefreshOnRendersIt() {
        var page = page();
        page.component();
        assertThat(((Nodes.SettingRow) node(page, "notice-vm.restart")).state().visible()).isFalse();
        notice = Optional.of(new SchemaPage.Notice("Restart required", "Restart Jasper to apply your changes."));
        listeners.forEach(Runnable::run);
        var row = (Nodes.SettingRow) node(page, "notice-vm.restart");
        assertThat(row.state().visible()).isTrue();
        assertThat(row.description()).isEqualTo("Restart Jasper to apply your changes.");
        kit(page, "notice-vm.restart-0", JButton.class).doClick();
        assertThat(restarts[0]).isEqualTo(1);
        page.dispose();
        assertThat(listeners).as("the page closes its refresh subscription").isEmpty();
    }

    @Test void aRevealWaitsForThePageToShowAndTheLatestRequestWins() {
        var page = page();
        var body = page.component();
        int listeners = body.getHierarchyListeners().length;
        page.reveal("vm.heap");
        assertThat(page.revealed()).isEqualTo("vm.heap");
        page.selected();
        assertThat(page.revealed()).as("a page that is not showing keeps the request").isEqualTo("vm.heap");
        assertThat(body.getHierarchyListeners()).hasSize(listeners + 1);
        page.reveal("other");
        page.selected();
        assertThat(page.revealed()).as("the later request replaces the pending one").isEqualTo("other");
        assertThat(body.getHierarchyListeners()).as("one listener waits, however often it is selected").hasSize(listeners + 1);
        page.dispose();
        assertThat(body.getHierarchyListeners()).as("dispose stops waiting").hasSize(listeners);
        page.selected();
        assertThat(body.getHierarchyListeners()).as("a disposed page waits for nothing").hasSize(listeners);
    }

    @Test void aRevealWithAValueStagesItAsTheRowsEdit() {
        var page = page();
        page.component();
        page.reveal("vm.heap=4096");
        page.selected();
        assertThat(page.externals().getFirst().pending()).isEqualTo(4096);
        assertThat(kit(page, "vm.heap.input", JTextField.class).getText()).isEqualTo("4096");
        assertThat(page.modified()).isTrue();
        assertThat(saves).as("staged, not saved").isEmpty();
        assertThat(page.revealed()).as("the row still waits to be focused").isEqualTo("vm.heap");
        kit(page, "vm.heap.input", JTextField.class).setText("3072");
        page.selected();
        assertThat(page.externals().getFirst().pending()).as("staged once").isEqualTo(3072);
    }

    @Test void aRevealedValueIsCheckedLikeTypedOne() {
        var page = page();
        page.component();
        page.reveal("vm.heap=256");
        page.selected();
        assertThat(page.externals().getFirst().error()).isEqualTo("Use 512 MiB or more.");
        assertThat(page.valid()).isFalse();
    }

    @Test void aDisabledRowOrAMalformedValueStagesNothing() {
        enabled = false;
        var disabled = page();
        disabled.component();
        disabled.reveal("vm.heap=4096");
        disabled.selected();
        assertThat(disabled.externals().getFirst().pending()).isNull();
        assertThat(disabled.modified()).isFalse();
        enabled = true;
        var garbled = page();
        garbled.component();
        garbled.reveal("vm.heap=lots");
        garbled.selected();
        assertThat(garbled.externals().getFirst().pending()).isNull();
        assertThat(garbled.revealed()).isEqualTo("vm.heap");
    }

    @Test void searchFindsTheExternalRow() {
        var page = page();
        page.component();
        assertThat(page.searchTerms()).contains("Maximum heap size");
        page.highlight("xmx");
        assertThat(page.externals().getFirst().highlighted()).isTrue();
    }
}
