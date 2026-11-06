package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.InsertedText;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TextTarget;
import dev.jasper.sdk.ui.ViewSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakePaletteTest {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste"), NAME = new PaletteVerb("name", "Name it");

    /** Two rows; "paste" pastes the title into the target, "name" asks for a name and reopens elsewhere. */
    static final class Things implements PaletteScope {
        final List<String> log = new ArrayList<>();
        @Override public ScopeSpec spec() {
            return ScopeSpec.of("test.a.things", "Things", "Search things", List.of(PASTE, NAME)).withShortcutActionId("test.a.open");
        }
        @Override public PaletteResults search(String query, PaletteQuery context) {
            log.add("search:" + query + ":" + context.window().id());
            return PaletteResults.of(List.of(PaletteRow.of("one", "One").withToken("t1"), PaletteRow.of("two", "Two").withEnabled(false)));
        }
        @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (!verb.equals(NAME)) return Optional.empty();
            return Optional.of(new PaletteStep("Name " + row.title(), List.of(new PaletteStep.Field("name", "Name", row.title())),
                (values, done) -> done.accept(values.get("name").isBlank() ? PaletteStep.Result.error("Give it a name")
                    : PaletteStep.Result.reopen("test.b.other", Optional.of("row-" + values.get("name")), Optional.of(values.get("name"))))));
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            log.add("execute:" + row.id() + ":" + verb.id() + ":" + row.token());
            context.target().ifPresent(pane -> pane.paste(row.title()));
        }
        @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
    }

    @Test void aScopeIsRegisteredSearchedSteppedAndRunWithItsOwnRowsAndHandles() {
        try (var host = new FakePluginHost()) {
            var things = new Things();
            var context = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT)), Set.of(), Set.of(), c -> {
                context.set(c);
                c.actions().register(ActionSpec.of("test.a.open", "Things…"), invoked -> c.palette().open(invoked.window(), "test.a.things", Optional.empty(), Optional.empty()));
                c.palette().register(things);
            });
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t");
            UUID pane = host.addTerminalPane(tab, FakeTerminalsTest.info("zsh"));
            assertThat(host.scopes()).containsExactly("test.a.things|Things|paste,name");
            assertThat(host.scopesInAll()).containsExactly("test.a.things");
            assertThat(host.searchScope("test.a.things", "on", window, pane)).containsExactly("one|One|true", "two|Two|false");
            assertThat(things.log).containsExactly("search:on:" + window);
            assertThat(host.availableInScope("test.a.things", "one", "paste", window, pane)).isTrue();
            assertThat(host.availableInScope("test.a.things", "two", "paste", window, pane)).isFalse();
            assertThat(host.stepInScope("test.a.things", "one", "paste", window, pane)).isEmpty();
            assertThat(host.stepInScope("test.a.things", "one", "name", window, pane)).contains("Name One");
            assertThat(host.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", " "))).isEqualTo("error:Give it a name");
            assertThat(host.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", "x"))).isEqualTo("reopen:test.b.other:row-x:x");
            host.executeInScope("test.a.things", "one", "paste", window, pane);
            assertThat(things.log).contains("execute:one:paste:t1");
            assertThat(host.sent(pane)).containsExactly("paste:One");
            assertThat(host.invoke("test.a.open", window, pane)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " test.a.things - -");
            host.stopAll();
            assertThat(host.scopes()).isEmpty();
        }
    }

    @Test void registrationIsGatedNamespacedAndBoundToOwnActionsAndNoticesAndTheEditorAreRecorded() {
        try (var host = new FakePluginHost()) {
            var bare = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.bare", "Bare", "1.0.0", Set.of()), Set.of(), Set.of(), bare::set);
            assertThatThrownBy(() -> bare.get().palette().register(new Things())).isInstanceOf(MissingCapabilityException.class);
            var a = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(), a::set);
            assertThatIllegalArgumentException().as("the shortcut action must exist").isThrownBy(() -> a.get().palette().register(new Things()));
            a.get().actions().register(ActionSpec.of("test.a.open", "Open"), invoked -> { });
            Subscription first = a.get().palette().register(new Things());
            assertThatIllegalArgumentException().as("duplicate id").isThrownBy(() -> a.get().palette().register(new Things()));
            var b = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.b", "B", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(), b::set);
            assertThatIllegalArgumentException().as("another plugin's namespace").isThrownBy(() -> b.get().palette().register(new Things()));
            first.close();
            assertThat(host.scopes()).isEmpty();
            a.get().notifications().group().error("It broke", "");
            a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
            assertThat(host.notifications().lines()).containsExactly("test.a|ERROR|It broke|");
            assertThat(host.openedInEditor()).containsExactly(Path.of("/tmp/x.toml"));
        }
    }

    /**
     * Since 0.29.0: a query with no pane names the window's current item while it takes inserted text; an
     * {@code addTextTarget} tab records what is inserted, and stops taking it once closed; a plugin's own content tab
     * takes text only while its insertion is set, and one that throws is recorded as the host's failure. On the UI
     * thread, as the kit is.
     */
    @Test void aQueryWithNoPaneNamesTheWindowsTextTargetAndInsertedTextReachesIt() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(this::textTargets);
    }

    private void textTargets() {
        try (var host = new FakePluginHost()) {
            var queries = new ArrayList<PaletteQuery>();
            var context = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT)), Set.of(), Set.of(), c -> {
                context.set(c);
                c.palette().register(new PaletteScope() {
                    @Override public ScopeSpec spec() { return ScopeSpec.of("test.a.texts", "Texts", "Search texts", List.of(PASTE)); }
                    @Override public PaletteResults search(String query, PaletteQuery asked) {
                        queries.add(asked);
                        return PaletteResults.of(List.of());
                    }
                    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery asked) { }
                    @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
                });
            });
            UUID window = host.addTerminalWindow(), target = host.addTextTarget(window);
            host.searchScope("test.a.texts", "", window, null);
            TextTarget kept = queries.getLast().textTarget().orElseThrow();
            assertThat(kept.isOpen()).isTrue();
            kept.insert(new InsertedText("ls", true));
            assertThat(host.insertedText(target)).containsExactly(new InsertedText("ls", true));
            host.closeTab(target);
            assertThat(kept.isOpen()).as("the kept target closes with its tab").isFalse();
            kept.insert(new InsertedText("pwd", false));
            assertThat(host.insertedText(target)).as("a closed tab takes nothing").containsExactly(new InsertedText("ls", true));
            host.searchScope("test.a.texts", "", window, null);
            assertThat(queries.getLast().textTarget()).as("a closed tab is no target").isEmpty();

            var view = context.get().kit().create(new ViewSpec(new Nodes.Text("body", ControlState.READY, "Note", Nodes.TextRole.BODY)), event -> { });
            ContentTab note = context.get().windows().openTab(new ContentSpec("note", "Note"), context.get().terminals().window(window).orElseThrow(), view);
            assertThat(host.insertText(note, new InsertedText("ls", false))).as("no insertion set").isFalse();
            note.setTextInsertion(Optional.of(text -> { throw new IllegalStateException("broken"); }));
            assertThat(host.insertText(note, new InsertedText("ls", false))).isTrue();
            assertThat(host.failures()).containsExactly("test.a content insertion: java.lang.IllegalStateException: broken");
        }
    }
}
