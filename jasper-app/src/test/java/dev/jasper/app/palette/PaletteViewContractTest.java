package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 16.1.9: PaletteViewContractTest. Both views honour one contract; a disagreement is a host bug. */
class PaletteViewContractTest {
    static final List<PaletteLook> LOOKS = List.of(PaletteLook.DEFAULT, PaletteLook.RUN_WINDOW);

    /** A scope whose rows are its names, whose runs are recorded, and whose third verb asks a step. */
    static PaletteScope scope(String id, String label, String noun, List<PaletteVerb> verbs, List<String> runs, String... rows) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public Nodes.Hue hue() { return Nodes.Hue.GREEN; }
            @Override public String itemNoun() { return noun; }
            @Override public PaletteResults search(String query, PaletteContext context) {
                var found = new ArrayList<PaletteRow>();
                for (String row : rows) if (row.contains(query)) found.add(new PaletteRow(row, row, "detail of " + row, null, null, true, null));
                return new PaletteResults(found.subList(0, Math.min(found.size(), context.maxResults())), null, null);
            }
            @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
                if (!verb.id().equals("ask")) return null;
                return new PaletteStep("Ask", List.of(new PaletteStep.Field("one", "One", "1"), new PaletteStep.Field("two", "Two", "2")),
                    (values, done) -> { runs.add(id + "/" + row.id() + ":ask:" + values); done.accept(PaletteStep.Result.done()); });
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { runs.add(id + "/" + row.id() + ":" + verb.id()); }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    record Harness(PaletteController controller, List<String> runs, List<String> browsed, ScopeRegistry registry, CommandHistory history) implements AutoCloseable {
        @Override public void close() { controller.close(); registry.close(); history.close(); }
        void open() { controller.open(PaletteScope.ALL_ID, PaletteTarget.none(), () -> true); }
        void query(String text) { controller.view().setQueryText(text); }
    }

    static Harness harness(PaletteLook look) {
        var runs = new ArrayList<String>();
        var browsed = new ArrayList<String>();
        var registry = new ScopeRegistry();
        var history = new CommandHistory();
        registry.register(scope("t.hosts", "Hosts", "session", List.of(new PaletteVerb("connect", "Connect"),
            new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT), new PaletteVerb("ask", "Ask")), runs, "mac", "minecraft"));
        registry.register(scope("t.snippets", "Snippets", "snippet", List.of(new PaletteVerb("insert", "Insert"),
            new PaletteVerb("insert_run", "Insert and run")), runs, "gcloud auth", "make"));
        var controller = new PaletteController(KitTestSupport.context(), look, registry, history, true, () -> { }, () -> { }, () -> false, () -> { },
            message -> { }, id -> { }, () -> "⇧⌘P", () -> browsed.add("browse"));
        return new Harness(controller, runs, browsed, registry, history);
    }

    @Test void enterRunsTheViewsPrimaryVerbAndTheOtherVerbsKeepTheirKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var look : LOOKS) try (var h = harness(look)) {
                h.open(); h.query("mac");
                h.controller().enterPressed(0);
                h.open(); h.query("mac");
                h.controller().enterPressed(1);
                h.open(); h.query("make");
                h.controller().enterPressed(1);
                assertThat(h.runs()).as(look.toString()).containsExactly("t.hosts/mac:connect", "t.hosts/mac:split", "t.snippets/make:insert_run");
                assertThat(h.controller().isOpen()).as("running a row closes both views").isFalse();
            }
        });
    }

    @Test void onlyTheRunWindowsTickedBoxMakesEnterSplitAndOnlyWhereASplitVerbExists() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var h = harness(PaletteLook.RUN_WINDOW)) {
                h.open(); h.query("mac");
                var view = (RunWindowView) h.controller().view();
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                h.controller().enterPressed(0);
                h.open();
                assertThat(view.splitTicked()).as("cleared when the window opens").isFalse();
                h.query("make");
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                assertThat(view.splitTicked()).as("a snippet cannot be ticked").isFalse();
                h.controller().enterPressed(0);
                assertThat(h.runs()).containsExactly("t.hosts/mac:split", "t.snippets/make:insert");
            }
            try (var h = harness(PaletteLook.DEFAULT)) {
                h.open(); h.query("mac");
                assertThat(h.controller().view()).isInstanceOf(CardView.class);
                h.controller().enterPressed(0);
                assertThat(h.runs()).as("the card's Enter is always verb 0").containsExactly("t.hosts/mac:connect");
            }
        });
    }

    @Test void escapeTabAndBackspaceBehaveTheSameInBothViews() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var look : LOOKS) try (var h = harness(look)) {
                h.open();
                assertThat(h.controller().tabPressed()).as(look + ": Tab is the palette's").isTrue();
                h.controller().selectTab("t.snippets");
                assertThat(h.controller().activeScopeId()).isEqualTo("t.snippets");
                assertThat(h.controller().backspacePressed()).as(look + ": Backspace in an empty query removes the chip").isTrue();
                assertThat(h.controller().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                h.controller().escape();
                assertThat(h.controller().isOpen()).as(look + ": Escape closes").isFalse();
            }
        });
    }

    /** The focused field of the step {@code view} shows. */
    static int stepFocus(PaletteView view) {
        return view instanceof RunWindowView run ? run.step().focusIndex() : ((CardView) view).stepFocusIndex();
    }

    /** Review Focus 5. */
    @Test void stepsBehaveTheSameInBothViews() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var look : LOOKS) try (var h = harness(look)) {
                h.open(); h.query("mac");
                h.controller().enterPressed(2);
                assertThat(h.controller().stepOpen()).as(look.toString()).isTrue();
                assertThat(h.controller().view().stepShowing()).isTrue();
                // Tab moves between the step's fields and wraps.
                int first = stepFocus(h.controller().view());
                assertThat(h.controller().tabPressed()).as(look + ": Tab is the step's").isTrue();
                assertThat(stepFocus(h.controller().view())).as(look + ": Tab moves to the next field").isEqualTo(first + 1);
                h.controller().tabPressed();
                assertThat(stepFocus(h.controller().view())).as(look + ": and wraps").isEqualTo(first);
                h.controller().escape();
                assertThat(h.controller().stepOpen()).as(look + ": Escape leaves the step").isFalse();
                assertThat(h.controller().isOpen()).isTrue();
                h.controller().enterPressed(2);
                h.controller().enterPressed(0);
                assertThat(h.runs()).as(look + ": the step's values, in field order").containsExactly("t.hosts/mac:ask:{one=1, two=2}");
            }
        });
    }

    @Test void theRunWindowCountsMatchesAndShowsTheOpenersShortcutAndBrowses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var h = harness(PaletteLook.RUN_WINDOW)) {
                h.open(); h.query("a");
                var view = (RunWindowView) h.controller().view();
                var list = (Nodes.ItemList) view.viewFor(RunWindowView.RESULTS).spec().root();
                var heading = list.items().getFirst();
                assertThat(((Nodes.Text) heading.title().getFirst()).text()).isEqualTo("Matches");
                assertThat(((Nodes.Text) heading.title().get(1)).text()).as("mac, minecraft, gcloud auth and make match").isEqualTo("1 of 4");
                assertThat(((Nodes.Text) view.viewFor(RunWindowView.SHORTCUT).spec().root()).text()).isEqualTo("⇧⌘P");
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.BROWSE));
                assertThat(h.browsed()).containsExactly("browse");
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.CANCEL));
                assertThat(h.controller().isOpen()).isFalse();
            }
        });
    }
}
