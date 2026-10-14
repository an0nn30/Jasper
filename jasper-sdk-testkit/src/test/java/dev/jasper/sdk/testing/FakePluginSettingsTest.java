package dev.jasper.sdk.testing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.settings.SettingsPanel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FakePluginSettingsTest {
    private final FakePluginHost host = new FakePluginHost();
    private static final PluginInfo INFO = new PluginInfo("test.fake", "Fake", "1.0.0", Set.of());

    @AfterEach void close() { host.close(); }

    @Test void declaredValuesComeFromTheFileWithReportsForBadAndUnknownKeys() {
        host.setSetting("test.fake", "font_size", 99L);
        host.setSetting("test.fake", "ratio", 2L);
        host.setSetting("test.fake", "mode", "safe");
        host.setSetting("test.fake", "words", List.of("ls", "cd"));
        host.setSetting("test.fake", "mystery", 1L);
        var size = new AtomicReference<Setting<Integer>>();
        var ratio = new AtomicReference<Setting<Double>>();
        var mode = new AtomicReference<Setting<Integer>>();
        var words = new AtomicReference<Setting<List<String>>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            ratio.set(context.settings().declare(SettingSpec.of("ratio", SettingKind.number(0.5, 3), 1.0).label("Ratio")));
            mode.set(context.settings().declare(SettingSpec.of("mode",
                SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2))), 1).label("Mode")));
            words.set(context.settings().declare(SettingSpec.of("words", SettingKind.stringList(), List.of()).label("Words")));
        });
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(ratio.get().get()).isEqualTo(2.0);
        assertThat(mode.get().get()).isEqualTo(2);
        assertThat(words.get().get()).containsExactly("ls", "cd");
        assertThat(host.settingsReports("test.fake")).containsExactlyInAnyOrder(
            "font_size: Use a whole number from 8–48.", "mystery: Unknown setting or table; ignored.");
        assertThat(host.declared("test.fake")).containsExactly("font_size", "ratio", "mode", "words");
    }

    @Test void setSavesTheRawValueAndListenersHearIt() throws Exception {
        var vim = new AtomicReference<Setting<Boolean>>();
        var seen = new ArrayList<Boolean>();
        int[] any = {0};
        host.start(INFO, Set.of(), Set.of(), context -> {
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            vim.get().onChanged(seen::add);
            context.settings().onChanged(() -> any[0]++);
        });
        vim.get().set(true).get();
        assertThat(vim.get().get()).isTrue();
        assertThat(seen).containsExactly(true);
        assertThat(any[0]).isEqualTo(1);
        assertThat(host.savedSettings("test.fake")).containsEntry("vim", true);
        host.setSetting("test.fake", "vim", "yes");
        assertThat(vim.get().get()).as("a wrong type keeps the last good value").isTrue();
        assertThat(host.settingsReports("test.fake")).singleElement().asString().startsWith("vim: Expected a boolean");
    }

    @Test void invalidSavesAndLateDeclarationsAreRefused() {
        var settings = new AtomicReference<PluginSettings>();
        var size = new AtomicReference<Setting<Integer>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("font_size", SettingKind.bool(), true).label("Again")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("font_size.inner", SettingKind.bool(), true).label("Inside")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("unlabelled", SettingKind.bool(), true)));
        });
        assertThat(host.active("test.fake")).isTrue();
        assertThatThrownBy(() -> size.get().set(99).get()).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatIllegalStateException().isThrownBy(() ->
            settings.get().declare(SettingSpec.of("late", SettingKind.bool(), false).label("Late")))
            .withMessage("Declare settings while the plugin starts: late");
        assertThat(settings.get().file()).isEqualTo(host.dataRoot().resolve("test.fake").resolve("test.fake.toml"));
    }

    @Test void aPluginsOwnPageGetsRowsAndStagesValues() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        var size = new AtomicReference<Setting<Integer>>();
        var settings = new AtomicReference<PluginSettings>();
        var seen = new ArrayList<String>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size").unit("pt")));
            context.settings().page(rows -> {
                var panel = context.kit().create(new dev.jasper.sdk.ui.ViewSpec(rows.row(size.get())), e -> {});
                seen.add("pending " + rows.pending(size.get()));
                rows.stage(size.get(), 20);
                seen.add("pending " + rows.pending(size.get()));
                assertThatIllegalArgumentException().isThrownBy(() -> rows.stage(size.get(), 99));
                rows.changed();
                return panel;
            });
            assertThatIllegalStateException().isThrownBy(() -> context.settings().page(rows -> {throw new AssertionError("Lazy page");}));
        });
        var component = host.settingsPanel("test.fake").orElseThrow();
        var row = (dev.jasper.sdk.ui.Nodes.SettingRow) host.settingsPanelView("test.fake").orElseThrow().spec().root();
        assertThat(row.title()).isEqualTo("Font size");
        // As the host renders it: a bounded integer is the Settings stepper with its declared unit.
        var input = (dev.jasper.sdk.ui.Nodes.Field) ((dev.jasper.sdk.ui.Nodes.Stack) row.control()).children().getFirst();
        assertThat(input.presentation()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldPresentation.STEPPER);
        assertThat(input.hint()).isEqualTo("pt");
        assertThat(seen).containsExactly("pending 13", "pending 20");
        assertThat(host.pendingSettings("test.fake")).containsEntry("font_size", 20);
        assertThat(host.settingsPanelChanges("test.fake")).isEqualTo(1);
        assertThat(size.get().get()).as("staging saves nothing").isEqualTo(13);
        assertThatIllegalStateException().isThrownBy(() -> settings.get().page(rows -> {throw new AssertionError("Lazy page");}));
    }); }

    /** As the host: a font hint's row is the font picker, a file hint's a path whose Browse takes the next queued file selection. */
    @Test void hintedRowsAreTheFontPickerAndAPathWithBrowse() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        var font = new AtomicReference<Setting<String>>();
        var log = new AtomicReference<Setting<String>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            font.set(context.settings().declare(SettingSpec.of("font", SettingKind.text(), "Menlo").label("Font").editor(EditorHint.font(true))));
            log.set(context.settings().declare(SettingSpec.of("log", SettingKind.text(), "").label("Log file").editor(EditorHint.file())));
            context.settings().page(rows -> context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",
                dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL, List.of(rows.row(font.get()), rows.row(log.get())))), e -> {}));
        });
        host.settingsPanel("test.fake").orElseThrow();
        var view = host.settingsPanelView("test.fake").orElseThrow();
        var installed = List.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        assertThat(control(view, 0)).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Choice.class, picker -> {
            assertThat(picker.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.ChoiceKind.SEARCHABLE);
            assertThat(picker.selectedId()).as("free entry owns the value").isEmpty();
            assertThat(picker.search().orElseThrow().entry()).contains("Menlo");
            assertThat(picker.search().orElseThrow().noMatches()).isEqualTo("No installed font matches");
            assertThat(picker.options()).extracting(dev.jasper.sdk.ui.Option::label).allSatisfy(family -> assertThat(installed).contains(family));
        });
        view.emit(new dev.jasper.sdk.ui.UiEvent.Edited("settings:font", "Courier New"));
        assertThat(host.pendingSettings("test.fake")).containsEntry("font", "Courier New");
        assertThat(control(view, 1)).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Field.class, path -> {
            assertThat(path.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldKind.PATH);
            assertThat(path.trailingAction()).isEqualTo("settings:log:browse");
        });
        view.emit(new dev.jasper.sdk.ui.UiEvent.Action("settings:log:browse"));
        assertThat(host.pendingSettings("test.fake")).as("nothing queued: Browse cancels").doesNotContainKey("log");
        Path chosen = host.dataRoot().resolve("app.log").toAbsolutePath().normalize();
        host.queueFileSelection(Optional.of(chosen));
        view.emit(new dev.jasper.sdk.ui.UiEvent.Action("settings:log:browse"));
        assertThat(host.pendingSettings("test.fake")).containsEntry("log", chosen.toString());
        assertThat(((dev.jasper.sdk.ui.Nodes.Field) control(view, 1)).value()).isEqualTo(chosen.toString());
        assertThat(host.failures()).isEmpty();
    }); }

    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */
    private static dev.jasper.sdk.ui.Nodes.Node control(FakeKitView view, int index) {
        var row = (dev.jasper.sdk.ui.Nodes.SettingRow) ((dev.jasper.sdk.ui.Nodes.Stack) view.spec().root()).children().get(index);
        return ((dev.jasper.sdk.ui.Nodes.Stack) row.control()).children().getFirst();
    }
}
