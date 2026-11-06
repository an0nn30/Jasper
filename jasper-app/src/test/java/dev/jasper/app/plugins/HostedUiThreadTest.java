package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Browser plans 4a, 4c and 5: the application host refuses openFile, hasModal, createForContent, isDefaultBrowser,
 * onDefaultBrowserChanged, setTextInsertion and a text target's calls off the UI thread; the testkit models no UI thread.
 */
class HostedUiThreadTest {
    @Test void theBrowserCoreCallsOffTheUiThreadThrow() {
        try (var harness = new AppContractTest().newHarness()) {
            UUID window = harness.addTerminalWindow();
            var context = new AtomicReference<PluginContext>();
            harness.start(new PluginInfo("test.off", "Off", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
            var owner = new AtomicReference<dev.jasper.sdk.terminal.WindowHandle>();
            harness.ui(() -> owner.set(context.get().terminals().window(window).orElseThrow()));
            assertThatIllegalStateException().isThrownBy(() -> context.get().platform().openFile(Path.of("/tmp/index.html")))
                .withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> context.get().windows().hasModal(owner.get())).withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> context.get().menus().createForContent("test.off.web", "Web"))
                .withMessageContaining("UI thread");
            assertThatIllegalStateException().as("browser plan 4c").isThrownBy(() -> context.get().platform().isDefaultBrowser())
                .withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> context.get().platform().onDefaultBrowserChanged(jasper -> { }))
                .withMessageContaining("UI thread");
            assertThat(harness.openedFiles()).isEmpty();
        }
    }

    /**
     * Browser plan 5 Task 10: a content tab's text insertion and a palette query's text target refuse the calling thread
     * off the UI thread, before they insert anything, while {@code copyOnSelect} answers on any thread.
     */
    @Test void textInsertionRefusesOtherThreadsAndCopyOnSelectAnswersOnAny() {
        try (var harness = new AppContractTest().newHarness()) {
            UUID window = harness.addTerminalWindow();
            var page = new AtomicReference<PluginContext>();
            harness.start(new PluginInfo("test.page", "Page", "1.0.0", Set.of()), Set.of(), Set.of(), page::set);
            var queries = new java.util.concurrent.CopyOnWriteArrayList<dev.jasper.sdk.palette.PaletteQuery>();
            harness.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE,
                dev.jasper.sdk.Capabilities.TERMINAL_INJECT)), Set.of(), Set.of(), context -> context.palette().register(recording(queries)));
            var tab = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
            var taken = new java.util.concurrent.CopyOnWriteArrayList<dev.jasper.sdk.ui.InsertedText>();
            harness.ui(() -> {
                var owner = page.get().terminals().window(window).orElseThrow();
                tab.set(page.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("page", "Page"), owner, page.get().kit().create(
                    new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body", dev.jasper.sdk.ui.ControlState.READY, "Page",
                        dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { })));
                tab.get().setTextInsertion(java.util.Optional.of(taken::add));
            });
            assertThatIllegalStateException().isThrownBy(() -> tab.get().setTextInsertion(java.util.Optional.empty()))
                .withMessageContaining("UI thread");
            harness.searchScope("test.a.words", "", window, null);
            var target = queries.getLast().textTarget().orElseThrow();
            assertThatIllegalStateException().isThrownBy(() -> target.insert(new dev.jasper.sdk.ui.InsertedText("ls", false)))
                .withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(target::isOpen).withMessageContaining("UI thread");
            assertThat(taken).isEmpty();
            harness.ui(() -> assertThat(target.isOpen()).as("the insertion is still set").isTrue());
            harness.copyOnSelect(true);
            assertThat(page.get().platform().copyOnSelect()).as("any thread").isTrue();
        }
    }

    /** A scope with one row that keeps every query it is given. */
    private static dev.jasper.sdk.palette.PaletteScope recording(java.util.List<dev.jasper.sdk.palette.PaletteQuery> queries) {
        return new dev.jasper.sdk.palette.PaletteScope() {
            @Override public dev.jasper.sdk.palette.ScopeSpec spec() {
                return dev.jasper.sdk.palette.ScopeSpec.of("test.a.words", "Words", "Search words",
                    java.util.List.of(new dev.jasper.sdk.palette.PaletteVerb("paste", "Paste")));
            }
            @Override public dev.jasper.sdk.palette.PaletteResults search(String query, dev.jasper.sdk.palette.PaletteQuery context) {
                queries.add(context);
                return dev.jasper.sdk.palette.PaletteResults.of(java.util.List.of(dev.jasper.sdk.palette.PaletteRow.of("ls", "ls")));
            }
            @Override public void execute(dev.jasper.sdk.palette.PaletteRow row, dev.jasper.sdk.palette.PaletteVerb verb,
                                          dev.jasper.sdk.palette.PaletteQuery context) { }
            @Override public dev.jasper.sdk.Subscription onChanged(Runnable listener) { return () -> { }; }
        };
    }
}
