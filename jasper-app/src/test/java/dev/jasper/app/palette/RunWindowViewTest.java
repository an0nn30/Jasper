package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.2 to 11.4: the Run window's regions, copy, primary verb, SPLIT checkbox and fixed list. */
class RunWindowViewTest {
    static KitContext enterprise() { return KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()); }
    static int m(KitContext context, DesignMetrics.Role role) { return context.metrics().value(role); }

    static void layout(RunWindowView view) {
        view.setSize(view.getPreferredSize());
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(view);
    }

    static Nodes.ItemList list(RunWindowView view) { return (Nodes.ItemList) view.viewFor(RunWindowView.RESULTS).spec().root(); }

    static List<String> listTexts(RunWindowView view) {
        var texts = new ArrayList<String>();
        for (ListItem item : list(view).items()) {
            var line = new StringBuilder(item.heading() ? "# " : "");
            // A heading's count is its second title node (Rulings 13).
            for (int i = 0; i < item.title().size(); i++) line.append(i > 0 ? " | " : "").append(((Nodes.Text) item.title().get(i)).text());
            texts.add(line.toString());
        }
        return texts;
    }

    static String text(RunWindowView view, String id) {
        var root = view.viewFor(id).spec().root();
        return switch (root) {
            case Nodes.Text text -> text.text();
            case Nodes.Button button -> button.label();
            case Nodes.Stack stack -> ((Nodes.Text) stack.children().get(1)).text();
            default -> throw new AssertionError(root);
        };
    }

    static boolean enabled(RunWindowView view, String id) { return view.viewFor(id).spec().root().state().enabled(); }

    static List<String> scopeLabels(RunWindowView view) {
        return ((Nodes.MenuButton) view.viewFor(RunWindowView.SCOPES).spec().root()).items().stream().map(dev.jasper.ui.model.MenuItem::label).toList();
    }

    /** Spec 11.2: the live COMMAND_PALETTE keycap and each scope registered by the time the window opens. */
    @Test void openingReadsTheLiveShortcutAndTheScopesRegisteredSince() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var scopes = new ArrayList<PaletteScope>(List.of(RunWindowFixtures.COMMANDS));
            var shortcut = new String[] {""};
            var host = new PaletteView.Host(query -> { }, (entry, verb) -> { }, text -> java.util.Optional.empty(), scope -> { },
                () -> List.copyOf(scopes), () -> shortcut[0], () -> { }, () -> { });
            try (var view = new RunWindowView(enterprise(), true, host)) {
                assertThat(text(view, RunWindowView.SHORTCUT)).as("unbound when built").isEmpty();
                assertThat(scopeLabels(view)).containsExactly("All", "Commands");
                scopes.add(RunWindowFixtures.SNIPPETS);
                shortcut[0] = "\u21e7\u2318P";
                view.opened();
                assertThat(text(view, RunWindowView.SHORTCUT)).isEqualTo("\u21e7\u2318P");
                assertThat(scopeLabels(view)).as("a scope registered after the window was built").containsExactly("All", "Commands", "Snippets");
                shortcut[0] = "\u2318K";
                view.refreshTheme();
                assertThat(text(view, RunWindowView.SHORTCUT)).as("a rebinding").isEqualTo("\u2318K");
            }
        });
    }

    @Test void theRegionsSitWhereTheMockPutsThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var context = enterprise();
            try (var view = RunWindowFixtures.view(context, new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                layout(view);
                assertThat(view.getWidth()).as("RUN_WINDOW_WIDTH inside a 1 px frame").isEqualTo(462);
                int hair = 1, side = m(context, DesignMetrics.Role.RUN_WINDOW_SIDE), top = hair + 24 + hair;
                assertThat(view.viewFor(RunWindowView.HELP).getBounds()).isEqualTo(new Rectangle(hair + side, top + 10, 436, view.viewFor(RunWindowView.HELP).getHeight()));
                Rectangle field = view.fieldBox().getBounds();
                int ring = m(context, DesignMetrics.Role.FOCUS_RING);
                assertThat(field.x + ring).as("the field box at the label column").isEqualTo(hair + side + 48);
                assertThat(field.x + field.width - ring).as("to the right padding").isEqualTo(462 - hair - side);
                assertThat(field.height - 2 * ring).isEqualTo(m(context, DesignMetrics.Role.FIELD_HEIGHT));
                Rectangle box = view.listBox().getBounds();
                assertThat(box.x).isEqualTo(hair + side);
                assertThat(box.width).isEqualTo(436);
                assertThat(box.y).isEqualTo(field.y + ring + m(context, DesignMetrics.Role.FIELD_HEIGHT) + 10);
                assertThat(box.height).as("8 rows and two headings (Rulings 5)").isEqualTo(2 + 8 * 21 + 2 * 19);
                var primary = view.viewFor(RunWindowView.PRIMARY).getBounds();
                var cancel = view.viewFor(RunWindowView.CANCEL).getBounds();
                var browse = view.viewFor(RunWindowView.BROWSE).getBounds();
                assertThat(List.of(primary.width, cancel.width, browse.width)).as("at least the mock's 70").allMatch(width -> width >= 70);
                assertThat(browse.x + browse.width).isEqualTo(462 - hair - side);
                assertThat(cancel.x + cancel.width + 6).isEqualTo(browse.x);
                assertThat(primary.x + primary.width + 6).isEqualTo(cancel.x);
                assertThat(view.getHeight() - hair - 40 + 8).as("buttons 8 into the footer").isEqualTo(primary.y);
                var close = view.viewFor(RunWindowView.CLOSE).getBounds();
                assertThat(close.x + close.width).isEqualTo(462 - hair - 5);
                assertThat(close.y + close.height / 2).isEqualTo(hair + 12);
                var split = view.viewFor(RunWindowView.SPLIT).getBounds();
                assertThat(split.x + ring).as("the box at the side padding").isEqualTo(hair + side);
                var label = view.viewFor(RunWindowView.SPLIT_LABEL).getBounds();
                assertThat(label.x).isEqualTo(hair + side + m(context, DesignMetrics.Role.CHECKBOX_BOX) + m(context, DesignMetrics.Role.CHECKBOX_LABEL_GAP));
                assertThat(view.getHeight()).isEqualTo(view.getPreferredSize().height);
            }
        });
    }

    @Test void justOpenedReadsAsTheMock() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                assertThat(listTexts(view)).containsExactly("# Recent", "Mac", "Install 'jasper' Command in PATH", "wiremock run", "# Most used",
                    "New Editor File", "settings.gradle.kts", "gcloud auth", "VendorDetailsService.java", "THD Crypto: Encrypt / Decrypt…");
                assertThat(text(view, RunWindowView.TITLE)).isEqualTo("Run");
                assertThat(text(view, RunWindowView.SHORTCUT)).isEqualTo("⇧⌘P");
                assertThat(text(view, RunWindowView.HELP)).isEqualTo(RunCopy.HELP);
                assertThat(text(view, RunWindowView.OPEN)).isEqualTo("Open:");
                assertThat(text(view, RunWindowView.DETAIL_LABEL)).isEqualTo("Session:");
                assertThat(text(view, RunWindowView.DETAIL_VALUE)).isEqualTo("dustin@192.168.1.119:22");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Connect");
                assertThat(text(view, RunWindowView.CANCEL)).isEqualTo("Cancel");
                assertThat(text(view, RunWindowView.BROWSE)).isEqualTo("Browse…");
                assertThat(text(view, RunWindowView.SPLIT_LABEL)).isEqualTo("Open in a split pane");
                assertThat(enabled(view, RunWindowView.SPLIT)).as("a session has a split verb").isTrue();
                var mac = list(view).items().get(1);
                assertThat(((Nodes.Icon) mac.leading().orElseThrow()).size()).isEqualTo(Nodes.IconSize.SWATCH);
                assertThat(((Nodes.Icon) mac.leading().orElseThrow()).hue()).isEqualTo(Nodes.Hue.GREEN);
                assertThat(((Nodes.Text) mac.subtitle().getFirst()).role()).as("the detail in the data face").isEqualTo(Nodes.TextRole.MONO);
                assertThat(mac.contextActions().orElseThrow()).extracting(dev.jasper.ui.model.MenuItem::label)
                    .as("the second and third verbs").containsExactly("Connect in split", "Edit");
                var crypto = list(view).items().get(9);
                assertThat(((Nodes.Text) crypto.trailing().getFirst()).text()).isEqualTo("⌃⌥⇧C");
                var scopes = (Nodes.MenuButton) view.viewFor(RunWindowView.SCOPES).spec().root();
                assertThat(scopes.items()).extracting(dev.jasper.ui.model.MenuItem::label).containsExactly("All", "Commands", "SSH", "Snippets", "Files");
                assertThat(scopes.icon()).isEqualTo("triangle.down");
                var help = (Nodes.Stack) view.viewFor(RunWindowView.HELP).spec().root();
                assertThat(help.role()).isEqualTo(Nodes.LayoutRole.PROMPT_HEADER);
                assertThat(((Nodes.Icon) help.children().getFirst()).name()).isEqualTo("play.filled");
            }
        });
    }

    /** Review Focus 2. */
    @Test void aQueryShowsOneMatchesHeadingWithItsPosition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                var entries = List.<PaletteEntry>of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "SSH"),
                    RunWindowFixtures.item(RunWindowFixtures.SESSIONS, "a", "alpha", null, null), RunWindowFixtures.item(RunWindowFixtures.SESSIONS, "b", "beta", null, null),
                    new PaletteEntry.More(RunWindowFixtures.SESSIONS), new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Snippets"),
                    RunWindowFixtures.item(RunWindowFixtures.SNIPPETS, "c", "gamma", null, null));
                view.setEntries(entries, null, "Nothing found");
                assertThat(listTexts(view)).containsExactly("# Matches | 1 of 3", "alpha", "beta", "More in SSH…", "gamma");
                view.selectRelative(2);
                assertThat(listTexts(view).getFirst()).as("a More row blanks the count").isEqualTo("# Matches | ");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Open");
                assertThat(enabled(view, RunWindowView.PRIMARY)).isTrue();
                assertThat(text(view, RunWindowView.DETAIL_LABEL)).isEmpty();
                view.selectRelative(1);
                assertThat(listTexts(view).getFirst()).isEqualTo("# Matches | 3 of 3");
                assertThat(text(view, RunWindowView.DETAIL_LABEL)).isEqualTo("Snippet:");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Insert");
            }
        });
    }

    /** Review Focus 1, and spec 11.4's Enter contract in the Run window. */
    @Test void theSplitBoxFollowsTheSelectionAndClearsOnAScopeWithoutASplitVerb() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            try (var view = RunWindowFixtures.view(enterprise(), events)) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                view.executeSelected(0);
                assertThat(events).containsExactly("run:dev.jasper.remote.scope/mac:0");
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                assertThat(view.splitTicked()).isTrue();
                view.executeSelected(0);
                assertThat(events).as("ticked: Enter runs the SPLIT verb").endsWith("run:dev.jasper.remote.scope/mac:1");
                view.executeSelected(2);
                assertThat(events).as("Shift+Enter's verb is unchanged").endsWith("run:dev.jasper.remote.scope/mac:2");
                view.selectRelative(2);
                assertThat(view.splitTicked()).as("a snippet has no split verb").isFalse();
                assertThat(enabled(view, RunWindowView.SPLIT)).isFalse();
                assertThat(enabled(view, RunWindowView.SPLIT_LABEL)).isFalse();
                view.executeSelected(0);
                assertThat(events).endsWith("run:dev.jasper.snippets.scope/wiremock:0");
                view.selectRelative(-2);
                assertThat(view.splitTicked()).as("the box stays clear when it comes back").isFalse();
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                view.opened();
                assertThat(view.splitTicked()).as("cleared each time the window opens").isFalse();
            }
        });
    }

    /** Review Focus 3. */
    @Test void longTextNeverWidensTheWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                String path = "/very/long/".repeat(30) + "file.txt";
                view.setEntries(List.of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Files"),
                    RunWindowFixtures.item(RunWindowFixtures.FILES, "long", "x".repeat(200), path, "⌘K")), null, "");
                layout(view);
                assertThat(view.getWidth()).isEqualTo(462);
                var value = view.viewFor(RunWindowView.DETAIL_VALUE);
                assertThat(value.getX() + value.getWidth()).as("the detail line ends at the padding").isLessThanOrEqualTo(462 - 1 - 12);
            }
        });
    }

    /** Review Focus 4. */
    @Test void theListKeepsItsHeightWithOneRowOrNone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.typingAuth(), null, "Nothing found");
                layout(view);
                int one = view.listBox().getHeight();
                view.setEntries(List.of(), null, "Nothing found");
                layout(view);
                assertThat(view.listBox().getHeight()).isEqualTo(one).isEqualTo(view.listHeight());
                assertThat(RunWindowFixtures.texts(view.listBox())).containsExactly("Nothing found");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Open");
                assertThat(enabled(view, RunWindowView.PRIMARY)).isFalse();
                assertThat(text(view, RunWindowView.DETAIL_VALUE)).isEmpty();
            }
        });
    }

    /** Review Focus 5. */
    @Test void aStepRendersInTheListBoxAsInTheCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "");
                int height = view.getPreferredSize().height;
                view.showStep("Rebase", List.of(new PaletteStep.Field("branch", "Branch", "main"), new PaletteStep.Field("onto", "Onto", "")));
                layout(view);
                assertThat(view.stepShowing()).isTrue();
                assertThat(SwingUtilities.isDescendingFrom(view.step().panel(), view.listBox())).isTrue();
                assertThat(view.step().panel().isShowing() || view.step().panel().isVisible()).isTrue();
                assertThat(enabled(view, RunWindowView.PRIMARY)).isFalse();
                assertThat(enabled(view, RunWindowView.SPLIT)).isFalse();
                assertThat(view.getPreferredSize().height).as("the window keeps its height").isEqualTo(height);
                assertThat(view.stepValues()).containsEntry("branch", "main");
                view.hideStep();
                assertThat(view.stepShowing()).isFalse();
                assertThat(enabled(view, RunWindowView.PRIMARY)).isTrue();
            }
        });
    }

    @Test void theButtonsTheMenuAndAChosenScopeCallTheHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            try (var view = RunWindowFixtures.view(enterprise(), events)) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "");
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.CANCEL));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.CLOSE));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.BROWSE));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.PRIMARY));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.SCOPE + "dev.jasper.snippets.scope"));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.SCOPE + PaletteScope.ALL_ID));
                view.handle(new dev.jasper.ui.model.UiEvent.Action("verb:5:1"));
                assertThat(events).containsExactly("cancel", "cancel", "browse", "run:dev.jasper.remote.scope/mac:0",
                    "scope:dev.jasper.snippets.scope", "scope:all", "run:jasper.commands/new_editor_file:1");
                view.setChip(RunWindowFixtures.SNIPPETS);
                assertThat(text(view, RunWindowView.HELP)).as("a chosen scope's description").isEqualTo("Insert or run a saved command");
                assertThat(view.viewFor(RunWindowView.CHIP).isVisible()).isTrue();
                view.setChip(null);
                assertThat(view.viewFor(RunWindowView.CHIP).isVisible()).isFalse();
                assertThat(text(view, RunWindowView.HELP)).isEqualTo(RunCopy.HELP);
                view.setPlaceholder("Search everything");
                assertThat(((Nodes.Field) view.viewFor(RunWindowView.QUERY).spec().root()).hint()).as("no placeholder (Rulings 9)").isEmpty();
                assertThat(view.modeless()).isTrue();
            }
        });
    }
}
