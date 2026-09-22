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
            a.get().notices().error("It broke");
            a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
            assertThat(host.notices()).containsExactly("test.a: It broke");
            assertThat(host.openedInEditor()).containsExactly(Path.of("/tmp/x.toml"));
        }
    }
}
