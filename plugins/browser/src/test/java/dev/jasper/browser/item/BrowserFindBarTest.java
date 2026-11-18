package dev.jasper.browser.item;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.page.PageFind;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentCommand;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A browser item's find bar (spec section 10; plan 4b Task 5) on the testkit's fake host, its page's find a recording
 * one: the frames {@code main} and {@code child}, the focus in {@code main}, and each command {@code find.js} would be
 * called with. The frames' answers are fed as the message router hands them on, so nothing needs Chromium.
 */
class BrowserFindBarTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com");
    private final List<String> ran = new ArrayList<>();
    private final List<Runnable> waits = new ArrayList<>();
    private final List<Runnable> typing = new ArrayList<>(), typingCancelled = new ArrayList<>();
    private boolean typingHeld;
    private PageFind find;

    // Stores in memory, one for each BrowserItems a test makes.
    private final MemoryStores memory = new MemoryStores();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    /**
     * Plan 4b's W10: the bar searches once typing pauses for {@link BrowserFindBar#TYPING_WAIT}, not on every keystroke;
     * Enter, a toggle and an emptied query act at once, and closing the bar drops the search still waiting.
     */
    @Test void typingSearchesOnceItPauses() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            command(host, "find");
            PageTestAccess.findSelected(find, "main", search(0), "");
            ran.clear();
            typingHeld = true;
            view.type(BrowserFindBar.FIND, "n");
            view.type(BrowserFindBar.FIND, "ne");
            view.type(BrowserFindBar.FIND, "nee");
            assertThat(searches()).as("not on each keystroke").isEmpty();
            assertThat(typing).hasSize(3);
            assertThat(typingCancelled).as("each keystroke puts the search off again").containsExactly(typing.get(0), typing.get(1));
            typing.getLast().run();
            assertThat(searches()).containsExactly("main nee", "child nee");
            answer(1, 1);
            assertThat(status(view)).isEqualTo("2 matches");

            ran.clear();
            view.type(BrowserFindBar.FIND, "need");
            view.press(BrowserFindBar.FIND, "ENTER");
            assertThat(searches()).as("Enter searches for what is typed at once").containsExactly("main need", "child need");
            assertThat(typingCancelled).contains(typing.getLast());

            ran.clear();
            view.type(BrowserFindBar.FIND, "needl");
            view.emit(new UiEvent.Action(BrowserFindBar.CASE));
            assertThat(searches()).as("a toggle searches at once").containsExactly("main needl", "child needl");
            assertThat(typingCancelled).contains(typing.getLast());

            ran.clear();
            view.type(BrowserFindBar.FIND, "");
            assertThat(ran).as("an emptied query clears the page at once").containsExactly("main {\"action\":\"clear\"}",
                "child {\"action\":\"clear\"}");

            view.type(BrowserFindBar.FIND, "needle");
            Runnable waiting = typing.getLast();
            view.press(BrowserFindBar.FIND, "ESCAPE");
            assertThat(typingCancelled).as("closing drops it").contains(waiting);
            ran.clear();
            waiting.run();
            assertThat(searches()).as("even should its timer still fire").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void openSeedsTheQueryFromAOneLineSelection() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            command(host, "find");
            assertThat(bar(view).state().visible()).isTrue();
            assertThat(view.focused()).hasValue(BrowserFindBar.FIND);
            assertThat(ran).singleElement().asString().startsWith("main {\"action\":\"selection\"");
            PageTestAccess.findSelected(find, "main", search(0), "needle");
            assertThat(field(view).value()).isEqualTo("needle");
            assertThat(view.fieldSelection(BrowserFindBar.FIND)).as("selected, so typing replaces it").containsExactly(0, 6);
            assertThat(searches()).containsExactly("main needle", "child needle");
            answer(2, 1);
            assertThat(status(view)).isEqualTo("3 matches");

            ran.clear();
            command(host, "find");
            PageTestAccess.findSelected(find, "main", search(0), "");
            assertThat(searches()).as("the search under way stands").isEmpty();
            assertThat(status(view)).isEqualTo("3 matches");
            view.press(BrowserFindBar.FIND, "ESCAPE");
            ran.clear();
            command(host, "find");
            PageTestAccess.findSelected(find, "main", search(0), "two\nlines");
            assertThat(field(view).value()).as("a selection of two lines").isEqualTo("needle");
            assertThat(searches()).as("the last query searches again").containsExactly("main needle", "child needle");

            ran.clear();
            command(host, "find");
            long asked = search(0);
            view.type(BrowserFindBar.FIND, "typed");
            PageTestAccess.findSelected(find, "main", asked, "late");
            assertThat(field(view).value()).as("typing wins over a selection that comes later").isEqualTo("typed");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void theStatusSaysHowManyMatchOrThatThePatternIsInvalid() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            command(host, "find");
            assertThat(status(view)).isEmpty();
            view.type(BrowserFindBar.FIND, "x");
            assertThat(status(view)).as("until the frames answer").isEmpty();
            answer(0, 0);
            assertThat(status(view)).isEqualTo("No matches");
            view.type(BrowserFindBar.FIND, "xy");
            answer(0, 1);
            assertThat(status(view)).isEqualTo("1 match");
            view.type(BrowserFindBar.FIND, "xyz");
            answer(2, 3);
            assertThat(status(view)).isEqualTo("5 matches");
            view.emit(new UiEvent.Action(BrowserFindBar.REGEX));
            view.type(BrowserFindBar.FIND, "(");
            long search = search(ran.size() - 1);
            PageTestAccess.findAnswered(find, "main", search, 0, true);
            PageTestAccess.findAnswered(find, "child", search, 0, true);
            assertThat(status(view)).isEqualTo("Invalid pattern");
            ran.clear();
            view.type(BrowserFindBar.FIND, "");
            assertThat(status(view)).isEmpty();
            assertThat(ran).as("an empty query clears the page").containsExactly("main {\"action\":\"clear\"}", "child {\"action\":\"clear\"}");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Escape in the bar, as its Close button, closes it and clears every highlight; the page takes the focus back. */
    @Test void escapeClosesTheBarAndClearsEveryHighlight() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            command(host, "find");
            view.type(BrowserFindBar.FIND, "needle");
            answer(1, 1);
            ran.clear();
            view.press(BrowserFindBar.FIND, "ESCAPE");
            assertThat(bar(view).state().visible()).isFalse();
            assertThat(ran).containsExactly("main {\"action\":\"clear\"}", "child {\"action\":\"clear\"}");
            assertThat(find.result()).isEmpty();

            ran.clear();
            command(host, "find");
            assertThat(field(view).value()).as("the bar keeps its query").isEqualTo("needle");
            PageTestAccess.findSelected(find, "main", search(0), "");
            answer(1, 1);
            ran.clear();
            view.emit(new UiEvent.Action(BrowserFindBar.CLOSE));
            assertThat(bar(view).state().visible()).isFalse();
            assertThat(ran).containsExactly("main {\"action\":\"clear\"}", "child {\"action\":\"clear\"}");
            assertThat(bar(view).cancelAction()).as("Escape anywhere in the bar").isEqualTo(BrowserFindBar.CLOSE);
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void closingGivesTheFocusToThePage() throws Exception {
        onUiThread(host -> {
            var focused = new ArrayList<String>();
            var recording = PageTestAccess.recordingFind(List.of("main"), Optional.of("main"), ran, waits);
            var bar = new BrowserFindBar(() -> Optional.of(recording), () -> { }, focused::add);
            bar.open();
            bar.close();
            assertThat(focused).containsExactly(BrowserFindBar.FIND, BrowserItem.PAGE);
            bar.close();
            assertThat(focused).as("once").hasSize(2);
        });
    }

    @Test void theTogglesRunTheSearchAgain() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            command(host, "find");
            view.type(BrowserFindBar.FIND, "a");
            assertThat(options()).isEqualTo("false false");
            assertThat(toggle(view, BrowserFindBar.CASE)).isFalse();
            view.emit(new UiEvent.Action(BrowserFindBar.CASE));
            assertThat(options()).isEqualTo("true false");
            assertThat(toggle(view, BrowserFindBar.CASE)).isTrue();
            view.emit(new UiEvent.Action(BrowserFindBar.REGEX));
            assertThat(options()).isEqualTo("true true");
            assertThat(toggle(view, BrowserFindBar.REGEX)).isTrue();
            view.emit(new UiEvent.Action(BrowserFindBar.CASE));
            assertThat(options()).isEqualTo("false true");
            Nodes.Button matchCase = (Nodes.Button) Views.node(view.spec().root(), BrowserFindBar.CASE);
            assertThat(matchCase.icon()).isEqualTo("match.case");
            assertThat(((Nodes.Button) Views.node(view.spec().root(), BrowserFindBar.REGEX)).icon()).isEqualTo("regex");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Enter in the query goes to the next match and Shift+Enter to the one before, as the bar's arrows do. */
    @Test void enterAndShiftEnterGoToTheNextAndPreviousMatch() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            command(host, "find");
            view.type(BrowserFindBar.FIND, "needle");
            long search = search(ran.size() - 1);
            answer(2, 1);
            assertThat(field(view).trailingAction()).isEqualTo(BrowserFindBar.NEXT);
            assertThat(field(view).alternateAction()).isEqualTo(BrowserFindBar.PREVIOUS);
            ran.clear();
            view.press(BrowserFindBar.FIND, "ENTER");
            assertThat(ran).containsExactly(select("main", search, 1));
            view.emit(new UiEvent.Action(BrowserFindBar.NEXT));
            assertThat(ran).endsWith(select("main", search, -1), select("child", search, 0));
            view.emit(new UiEvent.Action(BrowserFindBar.PREVIOUS));
            assertThat(ran).endsWith(select("child", search, -1), select("main", search, 1));
            assertThat(status(view)).isEqualTo("3 matches");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Find Next and Find Previous with the bar closed show it with its last query and search for it again. */
    @Test void findNextWithTheBarClosedShowsItAndSearchesAgain() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            command(host, "find-next");
            assertThat(bar(view).state().visible()).isTrue();
            assertThat(ran).as("no query yet, and no selection asked for").isEmpty();
            view.type(BrowserFindBar.FIND, "needle");
            view.press(BrowserFindBar.FIND, "ESCAPE");
            ran.clear();
            command(host, "find-previous");
            assertThat(bar(view).state().visible()).isTrue();
            assertThat(searches()).containsExactly("main needle", "child needle");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** The main frame's next document ends the search; Enter then searches it again. */
    @Test void aNewDocumentEndsTheSearchAndEnterSearchesAgain() throws Exception {
        onUiThread(host -> {
            var item = item(host);
            FakeKitView view = host.contentView(ID, "example.com").orElseThrow();
            command(host, "find");
            view.type(BrowserFindBar.FIND, "needle");
            answer(1, 0);
            assertThat(status(view)).isEqualTo("1 match");
            PageTestAccess.loadStarted(item.page(), "https://example.com/next");
            assertThat(status(view)).isEmpty();
            ran.clear();
            view.press(BrowserFindBar.FIND, "ENTER");
            assertThat(searches()).containsExactly("main needle", "child needle");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void theBarFollowsThePageInTheItemsView() throws Exception {
        onUiThread(host -> {
            FakeKitView view = open(host);
            var root = (Nodes.Split) view.spec().root();
            assertThat(root.children().getLast().id()).as("the bar is the view's last row").isEqualTo(BrowserFindBar.ROOT);
            assertThat(bar(view).state().visible()).as("closed until asked").isFalse();
            assertThat(bar(view).role()).isEqualTo(Nodes.LayoutRole.INLINE);
            assertThat(field(view).kind()).isEqualTo(Nodes.FieldKind.SEARCH);
            assertThat(field(view).cancelAction()).isEqualTo(BrowserFindBar.CLOSE);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Jasper's Find keys on macOS, and the Editor's content keys on Linux and Windows, where Ctrl+Shift+G is Jasper's. */
    @Test void theFindKeysAreJaspersOnMacOsAndTheEditorsElsewhere() {
        assertThat(BrowserFindBar.findKey(true)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.META_DOWN_MASK));
        assertThat(BrowserFindBar.findAgainKey(false, true)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_DOWN_MASK));
        assertThat(BrowserFindBar.findAgainKey(true, true))
            .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(BrowserFindBar.findKey(false)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        assertThat(BrowserFindBar.findAgainKey(false, false)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        assertThat(BrowserFindBar.findAgainKey(true, false)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
    }

    @Test void theItemsFindCommandsUseThoseKeys() throws Exception {
        onUiThread(host -> {
            var item = item(host);
            boolean macOs = System.getProperty("os.name", "").startsWith("Mac");
            var commands = host.contentCommands(item.tab()).stream().filter(each -> each.id().startsWith("find")).toList();
            assertThat(commands).extracting(ContentCommand::id).containsExactly("find", "find-next", "find-previous");
            assertThat(commands).extracting(ContentCommand::title).containsExactly("Find…", "Find Next", "Find Previous");
            assertThat(commands).extracting(ContentCommand::shortcut).containsExactly(BrowserFindBar.findKey(macOs),
                BrowserFindBar.findAgainKey(false, macOs), BrowserFindBar.findAgainKey(true, macOs));
            assertThat(commands).allSatisfy(each -> assertThat(each.enabled()).isTrue());
        });
    }

    /** Opens a browser item whose page finds with {@link #find}, and answers its view. UI thread. */
    private FakeKitView open(FakePluginHost host) {
        item(host);
        return host.contentView(ID, "example.com").orElseThrow();
    }

    private BrowserItem item(FakePluginHost host) {
        var context = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            context.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        var item = new BrowserItems(context.get(), settings.get()::read, new DataStores(memory.make(), () -> false), Optional.empty(), page -> { })
            .open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
        find = PageTestAccess.recordingFind(List.of("main", "child"), Optional.of("main"), ran, waits);
        PageTestAccess.useFind(item.page(), find);
        item.findBar().useLater(this::typingWait);
        return item;
    }

    /**
     * The bar's wait after a keystroke: at once, or with {@link #typingHeld} recorded in {@link #typing} for the test to
     * run, its cancel recorded in {@link #typingCancelled}.
     */
    private Runnable typingWait(java.time.Duration delay, Runnable task) {
        assertThat(delay).isEqualTo(BrowserFindBar.TYPING_WAIT);
        if (!typingHeld) {
            task.run();
            return () -> { };
        }
        typing.add(task);
        return () -> typingCancelled.add(task);
    }

    private static void command(FakePluginHost host, String id) {
        host.contentCommands(host.contentTabs().getFirst()).stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow().run().run();
    }

    /** The two frames answer the last search with these counts. */
    private void answer(int main, int child) {
        long search = search(ran.size() - 1);
        PageTestAccess.findAnswered(find, "main", search, main, false);
        PageTestAccess.findAnswered(find, "child", search, child, false);
    }

    /** Each search command {@link #ran} holds, as its frame and query. */
    private List<String> searches() {
        return ran.stream().filter(each -> "search".equals(command(each).get("action")))
            .map(each -> each.substring(0, each.indexOf(' ')) + " " + command(each).get("query")).toList();
    }

    /** The last search command's Match case and Regular expression. */
    private String options() {
        var last = ran.reversed().stream().map(BrowserFindBarTest::command).filter(each -> "search".equals(each.get("action"))).findFirst().orElseThrow();
        return last.get("matchCase") + " " + last.get("regex");
    }

    private long search(int index) {
        return (Long) command(ran.get(index)).get("search");
    }

    private static Map<String, Object> command(String ran) {
        return StrictJson.object(ran.substring(ran.indexOf(' ') + 1), 1).orElseThrow();
    }

    private static String select(String frame, long search, int index) {
        return frame + " {\"action\":\"select\",\"search\":" + search + ",\"index\":" + index + "}";
    }

    private static Nodes.Stack bar(FakeKitView view) {
        return (Nodes.Stack) Views.node(view.spec().root(), BrowserFindBar.ROOT);
    }

    private static Nodes.Field field(FakeKitView view) {
        return Views.field(view, BrowserFindBar.FIND);
    }

    private static String status(FakeKitView view) {
        return ((Nodes.Text) Views.node(view.spec().root(), BrowserFindBar.STATUS)).text();
    }

    private static boolean toggle(FakeKitView view, String id) {
        return ((Nodes.Button) Views.node(view.spec().root(), id)).selected().orElseThrow();
    }

    private interface HostTest {
        void run(FakePluginHost host) throws Exception;
    }

    private static void onUiThread(HostTest test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try (var host = new FakePluginHost()) {
                test.run(host);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
