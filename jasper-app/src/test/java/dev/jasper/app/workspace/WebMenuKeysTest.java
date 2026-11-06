package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ContentCommand;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Browser's Web menu keys (browser spec section 7) as a window resolves them: the Browser's content actions with
 * the Web menu's five default keys, beside History's Search Shell History and SFTP's Back and Forward as those plugins
 * register them. They clash with nothing on either keymap. In a browser item, in its page or its address field, they run
 * the item's actions; in a terminal pane beside it Cmd+R still searches shell history and the others reach the shell,
 * Ctrl+H, the shell's Backspace, among them.
 */
class WebMenuKeysTest {
    private static final String BROWSER = "dev.jasper.browser", HISTORY = "dev.jasper.history.open";
    private static final String SFTP = "dev.jasper.remote.sftp.panel";

    /** One Web menu action and its keys, as the Browser's WebMenu registers them. */
    private record Web(String id, String macOs, String other) { }

    private static final List<Web> WEB = List.of(new Web(BROWSER + ".open_location", "cmd+l", "ctrl+l"),
        new Web(BROWSER + ".back", "cmd+[", "alt+left"), new Web(BROWSER + ".forward", "cmd+]", "alt+right"),
        new Web(BROWSER + ".reload", "cmd+r", "ctrl+r"), new Web(BROWSER + ".history", "cmd+y", "ctrl+h"));

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void theWebKeysClashWithNothingOnEitherKeymap() {
        var extensions = new ArrayList<KeyBindings.Extension>();
        extensions.add(new KeyBindings.Extension(HISTORY, Optional.of("cmd+r")));
        extensions.add(new KeyBindings.Extension("dev.jasper.remote.sftp.back", Optional.of("alt+left"), Optional.empty(), KeyContext.panel(SFTP)));
        extensions.add(new KeyBindings.Extension("dev.jasper.remote.sftp.forward", Optional.of("alt+right"), Optional.empty(), KeyContext.panel(SFTP)));
        for (Web web : WEB)
            extensions.add(new KeyBindings.Extension(web.id(), Optional.of(web.macOs()), Optional.of(web.other()), KeyContext.content(BROWSER)));
        for (boolean macOs : new boolean[] { true, false }) {
            var resolved = KeyBindings.defaults(macOs).withExtensions(extensions);
            assertThat(resolved.problems()).as("dropped defaults with macOS=" + macOs).isEmpty();
            for (Web web : WEB)
                assertThat(resolved.bindings().strokeFor(web.id())).as(web.id()).contains(stroke(macOs ? web.macOs() : web.other(), macOs));
            assertThat(resolved.bindings().strokeFor(HISTORY)).as("Search Shell History keeps its key")
                .contains(stroke(macOs ? "cmd+r" : "ctrl+shift+r", macOs));
        }
    }

    @Test void inABrowserItemTheWebKeysRunItsActionsAndInATerminalPaneBesideItCmdRStillSearchesShellHistory() throws Exception {
        for (boolean macOs : new boolean[] { true, false }) {
            edt(() -> {
                var owner = content(launcher(new ArrayDeque<>()), macOs);
                var model = new Contributions();
                owner.connectContributions(model);
                var ran = new ArrayList<String>();
                model.addAction(HISTORY, "Search Shell History", null, List.of(), Optional.of("cmd+r"), Optional.empty(),
                    KeyContext.GLOBAL, invocation -> ran.add("history"));
                for (Web web : WEB)
                    model.addAction(web.id(), web.id(), null, List.of(), Optional.of(web.macOs()), Optional.of(web.other()),
                        KeyContext.content(BROWSER), invocation -> ran.add(web.id()));
                var item = new JPanel();
                var address = new JTextField("https://example.com");
                var page = new JPanel();
                item.add(address);
                item.add(page);
                Tab browser = model.tabs().open(BROWSER, "browser:1", "example.com", owner.id(), item);
                browser.commands(List.of(new ContentCommand("copy", "Copy", null, true, () -> ran.add("copy")),
                    new ContentCommand("paste", "Paste", null, true, () -> ran.add("paste"))));
                owner.split(Side.RIGHT);
                TerminalPane pane = owner.currentPane();
                assertThat(pane).as("a terminal pane beside the browser item has the focus").isNotNull();
                String platform = "macOS=" + macOs + ": ";

                // The terminal pane: Search Shell History has its key, and the other Web keys reach the shell, Ctrl+R
                // (the shell's own history search) among them.
                assertThat(owner.dispatchShortcut(stroke(macOs ? "cmd+r" : "ctrl+shift+r", macOs), pane)).isTrue();
                assertThat(ran).as(platform + "Search Shell History").containsExactly("history");
                ran.clear();
                for (Web web : WEB) {
                    boolean history = macOs && web.macOs().equals("cmd+r");
                    assertThat(owner.dispatchShortcut(stroke(macOs ? web.macOs() : web.other(), macOs), pane))
                        .as(platform + web.id() + "'s key in a terminal pane " + (history ? "searches shell history" : "reaches the shell"))
                        .isEqualTo(history);
                }
                assertThat(ran).as(platform + "no Web action in a terminal pane").containsExactlyElementsOf(macOs ? List.of("history") : List.of());
                ran.clear();

                // The browser item: its page and its address field.
                browser.select();
                assertThat(owner.selectedContent()).isSameAs(browser);
                for (var focus : List.of(page, address)) {
                    for (Web web : WEB)
                        assertThat(owner.dispatchShortcut(stroke(macOs ? web.macOs() : web.other(), macOs), focus)).as(platform + web.id()).isTrue();
                    assertThat(ran).as(platform + "in the item, from " + focus.getClass().getSimpleName())
                        .containsExactlyElementsOf(WEB.stream().map(Web::id).toList());
                    ran.clear();
                }
                // Ordinary editing keys reach the page: on Linux and Windows Ctrl+C and Ctrl+V among them.
                List<String> editing = new ArrayList<>(List.of("a", "shift+a", "backspace", "left", "shift+left"));
                if (macOs) editing.addAll(List.of("alt+left", "alt+right", "cmd+a", "cmd+z"));
                else editing.addAll(List.of("ctrl+c", "ctrl+v", "ctrl+a", "ctrl+z", "ctrl+left"));
                for (String key : editing)
                    assertThat(owner.dispatchShortcut(stroke(key, macOs), page)).as(platform + key + " reaches the page").isFalse();
                assertThat(ran).isEmpty();

                // Jasper's Copy and Paste run the item's copy and paste; its address field keeps its own clipboard.
                KeyStroke copy = owner.bindings().strokeFor(ActionId.COPY).orElseThrow();
                KeyStroke paste = owner.bindings().strokeFor(ActionId.PASTE).orElseThrow();
                assertThat(owner.dispatchShortcut(copy, page)).isTrue();
                assertThat(owner.dispatchShortcut(paste, page)).isTrue();
                assertThat(ran).as(platform + "Jasper's Copy and Paste keys in the page").containsExactly("copy", "paste");
                ran.clear();
                assertThat(owner.dispatchShortcut(copy, address)).as("the field copies by itself").isFalse();
                assertThat(owner.dispatchShortcut(paste, address)).as("the field pastes by itself").isFalse();
                assertThat(ran).isEmpty();
                owner.invoke(ActionId.COPY);
                owner.invoke(ActionId.PASTE);
                assertThat(ran).as(platform + "Edit > Copy and Edit > Paste").containsExactly("copy", "paste");
            });
            closeOwners();
        }
    }

    private static KeyStroke stroke(String binding, boolean macOs) {
        return KeyBindings.parse(binding, macOs).orElseThrow();
    }
}
