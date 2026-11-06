package dev.jasper.browser.routing;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.DataStores;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Browser as Jasper's web browser (spec section 9), asked as the host asks it, through the testkit's
 * {@code FakePluginHost.openLink}: what opens with no prompt, where an item opens, what an OSC 8 target reuses, when a
 * link goes back to Jasper, and the "Open in Jasper?" prompt with its "Remember my choice". The items are the Browser's
 * own on the fake host, which never shows them, so no Chromium is needed.
 */
class BrowserRoleTest {
    private static final String ID = "dev.jasper.browser", TITLE = "Open in Jasper?";
    private static final String LINKS = "open_links_from_terminal", LOCAL_FILES = "open_local_web_files";
    private static final String LINK_TEXT = "Jasper can display web pages! Would you like to open this link in Jasper?";
    private static final String FILE_TEXT = "Jasper can display files like this in its built-in web browser! Would you like to open this link in Jasper?";
    private static final URI LINK = URI.create("https://example.com/docs");
    @TempDir Path temp;
    private Started started;

    // Stores in memory, one for each BrowserItems a test makes.
    private final MemoryStores memory = new MemoryStores();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    @AfterEach void closeHost() throws Exception {
        if (started != null) onUiThread(started.host::close);
    }

    @Test void aTabLinkOpensInTheNextTabAndASplitLinkInThatSplit() {
        assertThat(BrowserRole.placement(LinkPlacement.TAB)).isEqualTo(TabPlacement.NEXT_TAB);
        assertThat(BrowserRole.placement(LinkPlacement.SPLIT_RIGHT)).isEqualTo(TabPlacement.SPLIT_RIGHT);
        assertThat(BrowserRole.placement(LinkPlacement.SPLIT_DOWN)).isEqualTo(TabPlacement.SPLIT_DOWN);
    }

    @Test void aSplitClickOpensBesideItsPaneWithNoPromptUnlessLinksGoToTheDefaultBrowser() throws Exception {
        started = start();
        onUiThread(() -> {
            assertThat(started.host.browserPlugin()).contains(ID);
            assertThat(open(LINK, LinkPlacement.SPLIT_RIGHT, Optional.empty(), Optional.of(started.window))).isTrue();
            assertThat(started.host.windows()).as("no prompt").isEmpty();
            BrowserItem item = started.items.items().getLast();
            assertThat(item.url()).isEqualTo(LINK);
            assertThat(item.target()).isEmpty();
            assertThat(started.host.windowTabs(started.window)).as("a pane beside the clicked one, in its tab")
                .singleElement().satisfies(tab -> assertThat(tab).hasSize(2).first().isEqualTo(started.shell));
            assertThat(open(URI.create("https://example.com/down"), LinkPlacement.SPLIT_DOWN, Optional.empty(), Optional.of(started.window))).isTrue();
            assertThat(started.host.windowTabs(started.window)).singleElement().satisfies(tab -> assertThat(tab).hasSize(3));
            assertThat(started.host.windows()).isEmpty();

            started.host.setSetting(ID, LINKS, "default");
            assertThat(open(LINK, LinkPlacement.SPLIT_RIGHT, Optional.empty(), Optional.of(started.window))).as("back to Jasper").isFalse();
            assertThat(started.items.items()).hasSize(2);
            started.host.setSetting(ID, LINKS, "builtin");
            assertThat(open(LINK, LinkPlacement.SPLIT_DOWN, Optional.empty(), Optional.of(started.window))).isTrue();
            assertThat(started.items.items()).hasSize(3);
            assertThat(started.host.failures()).isEmpty();
        });
    }

    /**
     * Browser plan 4c Task 2 (spec section 9, rule 2): while Jasper is the system's default browser, a clicked web link
     * opens with no prompt whatever Open Links From Terminal says, as Jasper answers at each link; a local file still goes
     * by Open Local Web Files, and once Jasper is no longer the default the setting decides again.
     */
    @Test void asTheDefaultBrowserAClickedLinkOpensWithNoPromptEvenWithAlwaysAsk() throws Exception {
        started = start();
        Path page = page();
        URI split = URI.create("https://example.com/split"), tab = URI.create("https://example.com/tab");
        onUiThread(() -> {
            started.host.setDefaultBrowser(true);
            started.host.setSetting(ID, LINKS, "default");
            assertThat(open(split, LinkPlacement.SPLIT_RIGHT, Optional.empty(), Optional.of(started.window))).as("a split under Default Browser").isTrue();
            assertThat(open(tab, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).as("a tab under Default Browser").isTrue();
            started.host.setSetting(ID, LINKS, "ask");
            assertThat(open(LINK, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).as("Always Ask").isTrue();
        });
        onUiThread(() -> {
            assertThat(started.host.windows()).as("no prompt").isEmpty();
            assertThat(started.items.items()).extracting(BrowserItem::url).containsExactly(split, tab, LINK);
            assertThat(started.host.browsed()).as("nothing went back to the system browser").isEmpty();
            assertThat(open(page.toUri(), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
        });
        onUiThread(() -> {
            assertPrompt(form(), FILE_TEXT, "Use Default Editor");
            press(form().cancelAction());
            started.host.setDefaultBrowser(false);
            assertThat(open(LINK, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
        });
        onUiThread(() -> {
            assertPrompt(form(), LINK_TEXT, "Use Default Browser");
            press(form().cancelAction());
            assertThat(started.items.items()).hasSize(3);
            assertThat(started.host.failures()).isEmpty();
        });
    }

    @Test void anAddressFromOutsideOpensInTheNextTabWithNoPromptWhateverTheSettings() throws Exception {
        started = start();
        onUiThread(() -> {
            UUID second = started.host.addTerminalTab(started.window, "second");
            started.host.addTerminalPane(second, PaneInfo.unknown());
            started.host.focusTerminalPane(started.pane);
            started.host.setSetting(ID, LINKS, "default");
            started.host.setSetting(ID, LOCAL_FILES, "editor");
            assertThat(started.host.openLink(LINK, LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
            assertThat(started.host.windows()).as("no prompt").isEmpty();
            BrowserItem item = started.items.items().getLast();
            assertThat(item.url()).isEqualTo(LINK);
            assertThat(item.tab().window().id()).isEqualTo(started.window);
            assertThat(started.host.windowTabs(started.window)).as("a tab right after the shown one").hasSize(3).satisfies(tabs -> {
                assertThat(tabs.getFirst()).containsExactly(started.shell);
                assertThat(tabs.getLast()).containsExactly(second);
                assertThat(tabs.get(1)).hasSize(1).doesNotContain(started.shell, second);
            });
            assertThat(item.tab().isActive()).as("shown").isTrue();

            assertThat(started.host.openLink(URI.create("https://example.com/b"), LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.empty(), Optional.empty()))
                .as("with no window: the most recently active one").isTrue();
            assertThat(started.items.items().getLast().tab().window().id()).isEqualTo(started.window);
            assertThat(started.host.failures()).isEmpty();
        });
    }

    @Test void anAddressFromOutsideWithNoWindowOpenOpensOneForIt() throws Exception {
        started = start(false);
        onUiThread(() -> {
            assertThat(started.host.openLink(LINK, LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.empty(), Optional.empty())).isTrue();
            BrowserItem item = started.items.items().getLast();
            UUID window = item.tab().window().id();
            assertThat(item.tab().window().isOpen()).isTrue();
            assertThat(started.host.windowTabs(window)).singleElement().satisfies(tab -> assertThat(tab).hasSize(1));
            assertThat(started.host.failures()).isEmpty();
        });
    }

    @Test void aLinkThatWouldAskGoesBackToJasperWhileItsWindowShowsAModalOrWhenItHasNoWindow() throws Exception {
        started = start();
        Path page = page();
        onUiThread(() -> {
            var settings = started.context.windows().dialog(new DialogSpec("Settings", handle(), true));
            settings.show();
            assertThat(started.context.windows().hasModal(handle())).isTrue();
            assertThat(open(LINK, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).as("a modal: the system browser").isFalse();
            assertThat(open(page.toUri(), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).as("a modal: the Default editor").isFalse();
            assertThat(open(LINK, LinkPlacement.SPLIT_RIGHT, Optional.empty(), Optional.of(started.window))).as("a split asks nothing").isTrue();
            settings.close();
            assertThat(open(LINK, LinkPlacement.TAB, Optional.empty(), Optional.empty())).as("no window to ask in").isFalse();
            assertThat(open(page.toUri(), LinkPlacement.TAB, Optional.empty(), Optional.empty())).isFalse();
        });
        onUiThread(() -> {
            assertThat(started.host.windowView(ID, TITLE)).as("no prompt").isEmpty();
            assertThat(started.items.items()).as("only the split").hasSize(1);
            assertThat(started.host.browsed()).isEmpty();
            assertThat(started.host.openedFiles()).isEmpty();
            assertThat(started.host.failures()).isEmpty();
        });
    }

    @Test void aModalThatOpensBeforeThePromptShowsSendsTheLinkWhereJasperWouldThisOnce() throws Exception {
        started = start();
        Path page = page();
        var about = new AtomicReference<dev.jasper.sdk.ui.PluginDialog>();
        onUiThread(() -> {
            assertThat(open(LINK, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
            assertThat(open(page.toUri(), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
            about.set(started.context.windows().dialog(new DialogSpec("About", handle(), true)));
            about.get().show();
        });
        onUiThread(() -> {
            assertThat(started.host.windowView(ID, TITLE)).isEmpty();
            assertThat(started.host.browsed()).containsExactly(LINK);
            assertThat(started.host.openedFiles()).containsExactly(page);
            assertThat(started.items.items()).isEmpty();
            assertThat(started.host.savedSettings(ID)).doesNotContainKeys(LINKS, LOCAL_FILES);
            about.get().close();
            assertThat(started.host.failures()).isEmpty();
        });
    }

    @Test void aTargetLoadsIntoTheItemItNamesInAnyWindowWithoutBringingItForward() throws Exception {
        started = start();
        onUiThread(() -> {
            started.host.setSetting(ID, LINKS, "builtin");
            UUID other = started.host.addTerminalWindow();
            assertThat(open(URI.create("https://example.com/one"), LinkPlacement.TAB, Optional.of("docs"), Optional.of(started.window))).isTrue();
            BrowserItem named = started.items.items().getLast();
            assertThat(named.target()).contains("docs");
            assertThat(open(URI.create("https://example.com/elsewhere"), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
            assertThat(named.tab().isActive()).as("behind the next item").isFalse();

            assertThat(open(URI.create("https://example.com/two"), LinkPlacement.TAB, Optional.of("docs"), Optional.of(other))).isTrue();
            assertThat(started.items.items()).as("the same item").hasSize(2);
            assertThat(named.url()).isEqualTo(URI.create("https://example.com/two"));
            assertThat(named.tab().window().id()).as("where it is").isEqualTo(started.window);
            assertThat(named.tab().isActive()).as("not brought forward").isFalse();
            assertThat(started.host.windowTabs(other)).as("nothing opened in the other window").isEmpty();
            assertThat(open(URI.create("https://example.com/three"), LinkPlacement.SPLIT_DOWN, Optional.of("docs"), Optional.of(other))).isTrue();
            assertThat(named.url()).as("a split click on it too").isEqualTo(URI.create("https://example.com/three"));
            assertThat(started.items.items()).hasSize(2);

            assertThat(open(URI.create("https://example.com/api"), LinkPlacement.TAB, Optional.of("api"), Optional.of(other))).isTrue();
            BrowserItem api = started.items.items().getLast();
            assertThat(api).isNotSameAs(named);
            assertThat(api.target()).contains("api");
            assertThat(api.tab().window().id()).as("a new item for a new target, in the link's window").isEqualTo(other);
            assertThat(started.host.failures()).isEmpty();
        });

        // A link that asks loads into the named item once the user chooses Open in Jasper.
        onUiThread(() -> {
            started.host.setSetting(ID, LINKS, "ask");
            assertThat(open(URI.create("https://example.com/four"), LinkPlacement.TAB, Optional.of("docs"), Optional.of(started.window))).isTrue();
        });
        onUiThread(() -> {
            press(form().actions().getLast().action());
            assertThat(started.items.items()).hasSize(3);
            assertThat(started.items.find("docs").orElseThrow().url()).isEqualTo(URI.create("https://example.com/four"));
            assertThat(started.host.failures()).isEmpty();
        });
    }

    /**
     * Once Chromium has failed to start, or has stopped, no browser item could show a page: every link goes back to Jasper,
     * which opens it in the system browser or the Default editor, and nothing asks. While it starts or runs, links open.
     */
    @Test void onceChromiumCannotShowPagesEveryLinkGoesBackToJasperWithoutAPrompt() throws Exception {
        started = start();
        Path page = page();
        onUiThread(() -> {
            for (Chromium.State state : List.of(Chromium.State.STARTING, Chromium.State.RUNNING)) {
                started.chromium.set(state);
                assertThat(open(LINK, LinkPlacement.SPLIT_RIGHT, Optional.empty(), Optional.of(started.window))).as(state.name()).isTrue();
            }
            started.host.setSetting(ID, LINKS, "builtin");
            assertThat(open(LINK, LinkPlacement.TAB, Optional.of("docs"), Optional.of(started.window))).isTrue();
            started.host.setSetting(ID, LINKS, "ask");
            assertThat(started.items.items()).hasSize(3);
            for (Chromium.State state : List.of(Chromium.State.FAILED, Chromium.State.STOPPED)) {
                started.chromium.set(state);
                assertThat(open(LINK, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).as(state + ": would ask").isFalse();
                assertThat(open(page.toUri(), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).as(state + ": a local file").isFalse();
                assertThat(open(LINK, LinkPlacement.SPLIT_DOWN, Optional.empty(), Optional.of(started.window))).as(state + ": a split").isFalse();
                assertThat(open(URI.create("https://example.com/two"), LinkPlacement.TAB, Optional.of("docs"), Optional.of(started.window)))
                    .as(state + ": a target that names an open item").isFalse();
                assertThat(started.host.openLink(LINK, LinkOrigin.EXTERNAL, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window)))
                    .as(state + ": from outside").isFalse();
            }
        });
        onUiThread(() -> {
            assertThat(started.host.windowView(ID, TITLE)).as("no prompt").isEmpty();
            assertThat(started.items.items()).as("nothing more opened").hasSize(3);
            assertThat(started.items.find("docs").orElseThrow().url()).as("nothing loaded").isEqualTo(LINK);
            assertThat(started.host.failures()).isEmpty();
        });
    }

    /** An Open in Jasper that can no longer open, as when Chromium failed while the prompt showed, goes where Jasper would. */
    @Test void openInJasperAfterChromiumFailedSendsTheLinkWhereJasperWould() throws Exception {
        started = start();
        Path page = page();
        for (URI address : List.of(LINK, page.toUri())) {
            onUiThread(() -> assertThat(open(address, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue());
            onUiThread(() -> {
                started.chromium.set(Chromium.State.FAILED);
                press(button("Open in Jasper").action());
                assertThat(started.host.windows()).as("the prompt closed").isEmpty();
                started.chromium.set(Chromium.State.NOT_STARTED);
            });
        }
        onUiThread(() -> {
            assertThat(started.items.items()).isEmpty();
            assertThat(started.host.browsed()).containsExactly(LINK);
            assertThat(started.host.openedFiles()).containsExactly(page);
            assertThat(started.host.failures()).isEmpty();
        });
    }

    @Test void theLinkPromptAsksInTheLinksWindowWithItsTextTheToggleOffAndOpenInJasperAsPrimary() throws Exception {
        started = start();
        var second = new AtomicReference<UUID>();
        onUiThread(() -> {
            second.set(started.host.addTerminalWindow());
            assertThat(open(LINK, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
            assertThat(started.host.windows()).as("it shows once open has returned").isEmpty();
        });
        onUiThread(() -> {
            assertThat(started.host.dialogPresentation(ID, TITLE)).contains(DialogSpec.Presentation.OVERLAY);
            assertThat(started.context.windows().hasModal(handle())).as("in the link's window").isTrue();
            assertThat(started.context.windows().hasModal(started.context.terminals().window(second.get()).orElseThrow())).isFalse();
            assertPrompt(form(), LINK_TEXT, "Use Default Browser");
            assertThat(started.items.items()).as("nothing opened while it asks").isEmpty();
            press(form().cancelAction());
        });
        Path page = page();
        onUiThread(() -> assertThat(open(page.toUri(), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue());
        onUiThread(() -> {
            assertThat(started.host.dialogPresentation(ID, TITLE)).contains(DialogSpec.Presentation.OVERLAY);
            assertPrompt(form(), FILE_TEXT, "Use Default Editor");
            press(form().cancelAction());
            assertThat(started.host.failures()).isEmpty();
        });
    }

    @Test void cancelEscapeAndClosingThePromptOpenNothingAndWriteNothingEvenWithRememberOn() throws Exception {
        started = start();
        Path page = page();
        for (URI address : List.of(LINK, page.toUri())) {
            for (String how : List.of("cancel", "escape", "close")) {
                onUiThread(() -> assertThat(open(address, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue());
                onUiThread(() -> {
                    remember(true);
                    assertThat(toggle().checked()).as("the toggle shows the choice").isTrue();
                    switch (how) {
                        case "cancel" -> press(form().actions().get(1).action());
                        case "escape" -> press(form().cancelAction());
                        default -> assertThat(started.host.requestClose("dialog")).as("the prompt's own close").isTrue();
                    }
                    assertThat(started.host.windows()).as(how + " closed it").isEmpty();
                    assertThat(started.items.items()).as(how).isEmpty();
                    assertThat(started.host.browsed()).as(how).isEmpty();
                    assertThat(started.host.openedFiles()).as(how).isEmpty();
                    assertThat(started.host.savedSettings(ID)).as(how).doesNotContainKeys(LINKS, LOCAL_FILES);
                });
            }
        }
        onUiThread(() -> assertThat(started.host.failures()).isEmpty());
    }

    @Test void rememberMyChoiceWritesThePromptsOwnSettingForEachChoiceAndOnlyWhenOn() throws Exception {
        started = start();
        Path page = page();
        URI file = page.toUri();

        // Open in Jasper, remembered: builtin, and the next link opens with no prompt.
        answer(LINK, true, "Open in Jasper");
        onUiThread(() -> {
            assertThat(started.host.savedSettings(ID)).containsEntry(LINKS, "builtin").doesNotContainKey(LOCAL_FILES);
            assertThat(started.items.items()).singleElement().satisfies(item -> {
                assertThat(item.url()).isEqualTo(LINK);
                assertThat(item.tab().window().id()).isEqualTo(started.window);
            });
            assertThat(open(URI.create("https://example.com/next"), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue();
        });
        onUiThread(() -> {
            assertThat(started.host.windowView(ID, TITLE)).as("remembered").isEmpty();
            assertThat(started.items.items()).hasSize(2);
        });

        // Use Default Browser, remembered: default, and the address goes to the system browser.
        onUiThread(() -> started.host.setSetting(ID, LINKS, "ask"));
        answer(LINK, true, "Use Default Browser");
        onUiThread(() -> {
            assertThat(started.host.savedSettings(ID)).containsEntry(LINKS, "default");
            assertThat(started.host.browsed()).containsExactly(LINK);
            assertThat(started.items.items()).hasSize(2);
        });

        // Use Default Editor, remembered: editor, and the file goes to the Default editor.
        answer(file, true, "Use Default Editor");
        onUiThread(() -> {
            assertThat(started.host.savedSettings(ID)).containsEntry(LOCAL_FILES, "editor").containsEntry(LINKS, "default");
            assertThat(started.host.openedFiles()).containsExactly(page);
        });

        // Open in Jasper for a local file, remembered: builtin, and an item at its file: address.
        onUiThread(() -> started.host.setSetting(ID, LOCAL_FILES, "ask"));
        answer(file, true, "Open in Jasper");
        onUiThread(() -> {
            assertThat(started.host.savedSettings(ID)).containsEntry(LOCAL_FILES, "builtin");
            assertThat(started.items.items().getLast().url()).isEqualTo(file);
        });

        // Without the toggle, or with it switched on and off again, a choice writes nothing.
        onUiThread(() -> {
            started.host.setSetting(ID, LINKS, "ask");
            started.host.setSetting(ID, LOCAL_FILES, "ask");
        });
        answer(LINK, false, "Use Default Browser");
        onUiThread(() -> assertThat(open(page.toUri(), LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue());
        onUiThread(() -> {
            remember(true);
            remember(false);
            assertThat(toggle().checked()).isFalse();
            press(button("Open in Jasper").action());
        });
        onUiThread(() -> {
            assertThat(started.host.savedSettings(ID)).containsAllEntriesOf(Map.of(LINKS, "ask", LOCAL_FILES, "ask"));
            assertThat(started.host.browsed()).containsExactly(LINK, LINK);
            assertThat(started.items.items()).hasSize(4);
            assertThat(started.host.failures()).isEmpty();
        });
    }

    /** Opens {@code address} as a click in a tab, then answers its prompt with the button labelled {@code choice}. */
    private void answer(URI address, boolean remember, String choice) throws Exception {
        onUiThread(() -> assertThat(open(address, LinkPlacement.TAB, Optional.empty(), Optional.of(started.window))).isTrue());
        onUiThread(() -> {
            if (remember) remember(true);
            press(button(choice).action());
            assertThat(started.host.windows()).as("the prompt closed").isEmpty();
        });
    }

    /** The prompt: a SHEET titled "Open in Jasper?", its text, the toggle unticked, and its three buttons. */
    private static void assertPrompt(Nodes.Form form, String text, String useDefault) {
        assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
        assertThat(form.title()).isEqualTo(TITLE);
        assertThat(form.sections()).hasSize(2);
        assertThat(form.sections().getFirst()).isInstanceOfSatisfying(Nodes.Text.class, message -> {
            assertThat(message.text()).isEqualTo(text);
            assertThat(message.role()).isEqualTo(Nodes.TextRole.BODY);
        });
        assertThat(form.sections().getLast()).isInstanceOfSatisfying(Nodes.SettingRow.class, row -> {
            assertThat(row.title()).isEqualTo("Remember my choice");
            assertThat(row.control()).isInstanceOfSatisfying(Nodes.Toggle.class, toggle -> {
                assertThat(toggle.checked()).as("unticked").isFalse();
                assertThat(toggle.label()).isEqualTo("Remember my choice");
            });
        });
        assertThat(form.actions()).extracting(Nodes.Button::label).containsExactly(useDefault, "Cancel", "Open in Jasper");
        assertThat(form.actions()).extracting(Nodes.Button::kind)
            .containsExactly(Nodes.ButtonKind.SECONDARY, Nodes.ButtonKind.SECONDARY, Nodes.ButtonKind.PRIMARY);
        assertThat(form.cancelAction()).as("Escape is Cancel").isEqualTo(form.actions().get(1).action());
    }

    private boolean open(URI address, LinkPlacement placement, Optional<String> target, Optional<UUID> window) {
        return started.host.openLink(address, LinkOrigin.CLICK, placement, target, window);
    }

    private WindowHandle handle() {
        return started.context.terminals().window(started.window).orElseThrow();
    }

    /** An existing local web page. */
    private Path page() throws Exception {
        return Files.writeString(Files.createTempFile(temp, "page", ".html"), "<p>hello</p>").toRealPath();
    }

    private FakeKitView view() {
        return started.host.windowView(ID, TITLE).orElseThrow(() -> new AssertionError("no prompt"));
    }

    private Nodes.Form form() {
        return (Nodes.Form) view().spec().root();
    }

    private Nodes.Button button(String label) {
        return form().actions().stream().filter(button -> button.label().equals(label)).findFirst()
            .orElseThrow(() -> new AssertionError("no button " + label));
    }

    private Nodes.Toggle toggle() {
        return (Nodes.Toggle) ((Nodes.SettingRow) form().sections().getLast()).control();
    }

    private void remember(boolean on) {
        view().emit(new UiEvent.Toggled(toggle().id(), on));
    }

    private void press(String action) {
        view().emit(new UiEvent.Action(action));
    }

    private Started start() throws Exception {
        return start(true);
    }

    /**
     * A stand-in Browser on a fake host whose start declares the Browser's settings and registers its role over its
     * items, as the plugin's does; with {@code window}, one window holding one terminal tab and pane, active.
     */
    private Started start(boolean window) throws Exception {
        var result = new AtomicReference<Started>();
        onUiThread(() -> {
            var host = new FakePluginHost();
            UUID shown = null, shell = null, pane = null;
            if (window) {
                shown = host.addTerminalWindow();
                host.activateTerminalWindow(shown);
                shell = host.addTerminalTab(shown, "zsh");
                pane = host.addTerminalPane(shell, PaneInfo.unknown());
            }
            var context = new AtomicReference<PluginContext>();
            var items = new AtomicReference<BrowserItems>();
            var chromium = new AtomicReference<>(Chromium.State.NOT_STARTED);
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of("browser.provide")), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                var settings = BrowserSettings.declare(plugin.settings());
                items.set(new BrowserItems(plugin, settings::read, new DataStores(memory.make(), () -> false), page -> { }));
                plugin.platform().browser(new BrowserRole(plugin, items.get(), settings, chromium::get));
            });
            result.set(new Started(host, context.get(), items.get(), shown, shell, pane, chromium));
        });
        return result.get();
    }

    private static void onUiThread(ThrowingRunnable body) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        // What the body posted, such as the prompt's show, runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Started(FakePluginHost host, PluginContext context, BrowserItems items, UUID window, UUID shell, UUID pane,
                           AtomicReference<Chromium.State> chromium) { }
}
