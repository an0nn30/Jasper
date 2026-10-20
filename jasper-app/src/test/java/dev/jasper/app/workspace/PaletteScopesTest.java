package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandSearch;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteResults;
import dev.jasper.app.palette.PaletteRow;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteStep;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.palette.PaletteVerb;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.CommandPaletteShortcutsTest.*;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class PaletteScopesTest {
    /** A scope with no Swing in it: rows and verbs are data; execution records what it was asked to do. */
    static class FakeScope implements PaletteScope {
        final List<String> executed = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();
        int activations;
        boolean inAll = true;
        boolean inRecents = true;
        List<PaletteRow> rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"));
        PaletteStep.Result stepResult = PaletteStep.Result.done();
        final List<Map<String, String>> completed = new ArrayList<>();
        boolean deferCompletion;
        boolean reuseStep;
        PaletteStep cachedStep;
        java.util.function.Consumer<PaletteStep.Result> pending;
        @Override public String id() { return "test.fake"; }
        @Override public String label() { return "Fake"; }
        @Override public String description() { return "Fixture scope"; }
        @Override public String placeholder() { return "Search fake"; }
        @Override public boolean inAll() { return inAll; }
        @Override public boolean inRecents() { return inAll && inRecents; }
        @Override public List<PaletteVerb> verbs() {
            return List.of(new PaletteVerb("one", "One"), new PaletteVerb("two", "Two"), new PaletteVerb("three", "Three"));
        }
        @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
            if (!verb.id().equals("three")) return null;
            if (reuseStep && cachedStep != null) return cachedStep;
            return cachedStep = new PaletteStep("Fill " + row.title(),
                List.of(new PaletteStep.Field("first", "First", "pre"), new PaletteStep.Field("second", "Second", "")),
                (values, done) -> {
                    completed.add(values);
                    if (deferCompletion) pending = done; else done.accept(stepResult);
                });
        }
        @Override public void activated(PaletteContext context) { activations++; }
        @Override public PaletteResults search(String query, PaletteContext context) {
            String q = CommandSearch.normalize(query);
            return new PaletteResults(rows.stream()
                .filter(row -> q.isEmpty() || row.title().toLowerCase(Locale.ROOT).contains(q))
                .limit(context.maxResults()).toList(),
                q.isEmpty() ? "Most recent" : null, null);
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
            executed.add(row.id() + ":" + verb.id());
        }
        @Override public Subscription onChanged(Runnable listener) {
            listeners.add(listener);
            return new Subscription(() -> listeners.remove(listener));
        }
    }

    @Test void theCommandPaletteShortcutOpensSuggestionsWithTheSelectedRowsHintAlongTheBottom() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(new PaletteRow("alpha", "Alpha", "the alpha detail", null, null, true, null), PaletteRow.of("beta", "Beta"));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.chipText(card)).isNull();
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Search everything");
                var entries = PaletteTestSupport.entries(card);
                assertThat(entries.getFirst()).as("nothing used yet").isEqualTo("# Suggested");
                assertThat(entries).contains("test.fake/alpha", "test.fake/beta").noneMatch(entry -> entry.startsWith("mode:"));
                PaletteTestSupport.selectRow(card, "alpha");
                assertThat(PaletteTestSupport.hintBarTexts(card)).as("only the selected row's detail and its verbs")
                    .containsExactly("the alpha detail", "One ⏎", "Two ⌘⏎", "Three ⇧⏎");
                assertThat(fake.activations).isEqualTo(1);
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("al");
                assertThat(PaletteTestSupport.entries(card)).containsSubsequence("# Fake", "test.fake/alpha");
                palette.toggle();
                assertThat(palette.isOpen()).as("the shortcut closes it again").isFalse();
            }
        });
    }

    @Test void runRowsComeBackAsRecentlyAndMostUsedButOptedOutScopesNever() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"),
                    PaletteRow.of("delta", "Delta"));
                var quiet = new FakeScope() {
                    @Override public String id() { return "test.quiet"; }
                    @Override public String label() { return "Quiet"; }
                };
                quiet.inRecents = false;
                owner.scopes().register(fake);
                owner.scopes().register(quiet);
                var palette = owner.commandPalette(); var card = palette.component();
                for (String row : new String[] {"delta", "delta", "delta", "gamma", "beta", "alpha"}) {
                    palette.open("test.fake"); dev.jasper.app.palette.PaletteTestSupport.query(card).setText("");
                    PaletteTestSupport.selectRow(card, row); palette.enterPressed(0);
                }
                palette.open("test.quiet"); PaletteTestSupport.selectRow(card, "alpha"); palette.enterPressed(0);
                assertThat(quiet.executed).containsExactly("alpha:one");
                assertThat(palette.history().uses()).extracting(dev.jasper.app.commands.PaletteUse::scopeId).doesNotContain("test.quiet");
                palette.toggle();
                var entries = PaletteTestSupport.entries(card);
                assertThat(entries.subList(0, 4)).as("spec 13: three recent rows")
                    .containsExactly("# Recently used", "test.fake/alpha", "test.fake/beta", "test.fake/gamma");
                assertThat(entries.subList(4, 6)).as("ranked by use, without repeating the recents").containsExactly("# Most used", "test.fake/delta");
                assertThat(entries).noneMatch(entry -> entry.startsWith("test.quiet/"));
                fake.rows = List.of(PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"), PaletteRow.of("delta", "Delta"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(PaletteTestSupport.entries(card)).as("a row that is gone drops out").doesNotContain("test.fake/alpha")
                    .containsSubsequence("# Recently used", "test.fake/beta", "test.fake/gamma");
            }
        });
    }

    /** Review I1: a key queued before the field shows the chip's query must not bring the consumed prefix back. */
    @Test void aKeyQueuedBeforeTheChipSettlesKeepsThePrefixOutOfTheQuery() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("lima", "lima"));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                var query = PaletteTestSupport.query(card);
                query.setText("fake > ");
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                try { query.getDocument().insertString(query.getDocument().getLength(), "l", null); }
                catch (javax.swing.text.BadLocationException failure) { throw new AssertionError(failure); }
                PaletteTestSupport.settle();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(PaletteTestSupport.chipText(card)).isEqualTo("Fake");
                assertThat(PaletteTestSupport.queryText(card)).isEqualTo("l");
                assertThat(query.getText()).isEqualTo("l");
                assertThat(query.getCaretPosition()).isEqualTo(1);
                assertThat(PaletteTestSupport.entries(card)).as("the chip's query searches").contains("test.fake/alpha", "test.fake/lima");
            }
        });
    }

    @Test void typedPrefixesSetTheChipAndBackspaceRemovesIt() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("gt", "closes > right"), PaletteRow.of("alpha", "Alpha"));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("fake > al");
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(PaletteTestSupport.chipText(card)).isEqualTo("Fake");
                assertThat(PaletteTestSupport.queryText(card)).as("the prefix left the query").isEqualTo("al");
                PaletteTestSupport.settle();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.query(card).getText()).as("and the field once it settles").isEqualTo("al");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("test.fake/alpha");
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("closes > right");
                assertThat(palette.activeScopeId()).as("with a chip, '>' is text").isEqualTo("test.fake");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("test.fake/gt");
                assertThat(palette.backspacePressed()).as("the query is not empty").isFalse();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("");
                assertThat(palette.backspacePressed()).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.chipText(card)).isNull();
                assertThat(palette.backspacePressed()).as("no chip to remove").isFalse();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText(">");
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Type a command");
            }
        });
    }

    @Test void allListsEveryMatchHoweverFewRowsAreVisible() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                var rows = new ArrayList<PaletteRow>();
                for (int r = 0; r < 12; r++) rows.add(PaletteRow.of("alp" + r, "Alp " + r));
                fake.rows = rows;
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.setVisibleRows(2);
                assertThat(palette.visibleRows()).isEqualTo(2);
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("alp");
                var entries = PaletteTestSupport.entries(card);
                int section = entries.indexOf("# Fake");
                assertThat(entries.subList(section + 1, section + 13)).as("every match, in order")
                    .allMatch(entry -> entry.startsWith("test.fake/alp"));
                assertThat(entries).as("nothing is held back").noneMatch(entry -> entry.startsWith("more:"));
                palette.open("test.fake");
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(12);
            }
        });
    }

    @Test void aScopeThatOptsOutStaysOutOfAllButCanStillBeOpened() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope(); fake.inAll = false;
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(PaletteTestSupport.entries(card)).as("no unprompted rows").noneMatch(entry -> entry.startsWith("test.fake/"));
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("al");
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("# Fake").noneMatch(entry -> entry.startsWith("test.fake/"));
                assertThat(fake.activations).as("All does not activate it").isZero();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.entries(card)).contains("test.fake/alpha");
            }
        });
    }

    @Test void aScopeThatFailsIsSkippedInAllWhileTheOthersShow() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope() {
                    @Override public String id() { return "test.broken"; }
                    @Override public String label() { return "Broken"; }
                    @Override public PaletteResults search(String query, PaletteContext context) { throw new IllegalStateException("broken"); }
                });
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("alph");
                assertThat(palette.isOpen()).isTrue();
                assertThat(PaletteTestSupport.entries(card)).contains("# Fake").doesNotContain("# Broken");
            }
        });
    }

    @Test void tabIsIgnoredOutsideAStepAndKeepsTheChipAndQuery() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("be");
                assertThat(palette.tabPressed(false)).isTrue();
                assertThat(palette.tabPressed(true)).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.query(card).getText()).isEqualTo("be");
                palette.dismiss();
                assertThat(palette.tabPressed(false)).as("closed").isFalse();
            }
        });
    }

    @Test void upAndDownSkipSectionHeadersAcrossScopes() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                // A query no command title holds even as scattered letters ("alph" is in "Terminal: Catppuccin Mocha").
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("kumquat", "Kumquat"), PaletteRow.of("beta", "Beta"));
                owner.scopes().register(fake);
                var other = new FakeScope() {
                    @Override public String id() { return "test.other"; }
                    @Override public String label() { return "Other"; }
                };
                other.rows = fake.rows;
                owner.scopes().register(other);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("kumq");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("# Fake", "test.fake/kumquat", "# Other", "test.other/kumquat");
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isZero();
                palette.moveSelection(1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).as("past the Other header").isEqualTo(1);
                palette.moveSelection(-1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isZero();
                palette.moveSelection(50);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isEqualTo(1);
            }
        });
    }

    @Test void theHintBarShowsTheRowsDetailAndEveryVerbOfItsScopeWhichAreClickable() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(new PaletteRow("alpha", "Alpha", "alpha detail", "tag", null, true, null));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.hint(card)).isEqualTo("alpha detail");
                assertThat(PaletteTestSupport.hintActions(card)).containsExactly("One ⏎", "Two ⌘⏎", "Three ⇧⏎");
                PaletteTestSupport.clickHintAction(card, 0);
                assertThat(fake.executed).containsExactly("alpha:one");
                palette.open("test.fake");
                PaletteTestSupport.clickHintAction(card, 1);
                assertThat(fake.executed).containsExactly("alpha:one", "alpha:two");
                palette.open("test.fake");
                PaletteTestSupport.clickHintAction(card, 2);
                assertThat(palette.stepOpen()).isTrue();
                assertThat(PaletteTestSupport.hintBar(card).isVisible()).as("hidden in a step").isFalse();
                palette.escape();
                assertThat(PaletteTestSupport.hintBar(card).isVisible()).isTrue();
                palette.open(PaletteScope.COMMANDS_ID);
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("new tab");
                assertThat(PaletteTestSupport.hint(card)).as("a command's shortcut").isEqualTo("⌘T");
                assertThat(PaletteTestSupport.hintActions(card)).isEmpty();
            }
        });
    }

    @Test void verbsRouteEnterAndCmdEnterThroughTheOriginTarget() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                PaletteTestSupport.executeSelected(card);
                assertThat(palette.isOpen()).isFalse();
                palette.open("test.fake");
                PaletteTestSupport.executeSelected(card, 1);
                palette.open("test.fake");
                PaletteTestSupport.selectRelative(card, 1);
                PaletteTestSupport.executeSelected(card);
                assertThat(fake.executed).containsExactly("alpha:one", "alpha:two", "beta:one");
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("new tab");
                PaletteTestSupport.executeSelected(card, 1);
                assertThat(palette.isOpen()).as("Commands has no second verb").isTrue();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            }
        });
    }

    @Test void aRowFromAllRunsItsOwnScopesVerbs() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("beta");
                PaletteTestSupport.selectRow(card, "beta");
                PaletteTestSupport.executeSelected(card, 1);
                assertThat(fake.executed).containsExactly("beta:two");
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("beta");
                PaletteTestSupport.selectRow(card, "beta");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).as("the scope's own step").isTrue();
                assertThat(PaletteTestSupport.stepTitle(card)).isEqualTo("Fill Beta");
                palette.enterPressed(0);
                assertThat(fake.completed).hasSize(1);
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void aStepOpenedFromAllClosesInsteadOfRunningWhenItsScopeIsRemoved() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("beta");
                PaletteTestSupport.selectRow(card, "beta");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).as("the step opened from All").isTrue();
                registration.close();
                assertThat(palette.isOpen()).as("closed when the step's own scope is gone").isFalse();
                assertThat(fake.completed).as("the removed scope's completion never ran").isEmpty();
            }
        });
    }

    @Test void scopeChangesRefreshTheOpenListAndRemovingTheActiveScopeDismisses() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.entries(card).getFirst()).as("the scope's own section label").isEqualTo("# Most recent");
                PaletteTestSupport.selectRelative(card, 1);
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(3);
                assertThat(PaletteTestSupport.selectedRow(card).id()).isEqualTo("beta");
                registration.close();
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void scopesRegisteredOrRemovedWhileOpenUpdateTheSections() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("gam");
                fake.rows = List.of(PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(PaletteTestSupport.entries(card)).contains("# Fake", "test.fake/gamma");
                registration.close();
                assertThat(palette.isOpen()).as("All stays open").isTrue();
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("# Fake");
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void allBudgetsItsRowsAcrossManyScopesInsteadOfThrowing() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                // Eleven scopes of 30 rows are 330 item rows, over PaletteResults.MAX_ROWS (200);
                // All must budget instead of handing setEntries too many.
                for (int n = 0; n < 11; n++) {
                    int index = n;
                    var scope = new FakeScope() {
                        @Override public String id() { return "test.fake" + index; }
                        @Override public String label() { return "Fake" + index; }
                    };
                    var rows = new ArrayList<PaletteRow>();
                    for (int r = 0; r < 30; r++) rows.add(PaletteRow.of("row" + r, "Row " + r));
                    scope.rows = rows;
                    owner.scopes().register(scope);
                }
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("row");
                assertThat(palette.isOpen()).as("All opens without throwing").isTrue();
                assertThat(PaletteTestSupport.rowCount(card)).as("at most PaletteResults.MAX_ROWS (200) item rows").isEqualTo(200);
                var entries = PaletteTestSupport.entries(card);
                var headers = entries.stream().filter(entry -> entry.startsWith("# Fake")).toList();
                var expected = new ArrayList<String>();
                for (int n = 0; n < 7; n++) expected.add("# Fake" + n);
                assertThat(headers).as("sections in tab order, stopping once the budget is spent").containsExactlyElementsOf(expected);
                assertThat(entries.stream().filter(entry -> entry.startsWith("more:")).toList())
                    .as("only the scope the budget cut short offers the rest").containsExactly("more:test.fake6");
                PaletteTestSupport.selectRelative(card, 1000);
                palette.moveSelection(1);
                assertThat(PaletteTestSupport.selectedRow(card)).as("the More row is selected").isNull();
                palette.enterPressed(0);
                assertThat(palette.activeScopeId()).isEqualTo("test.fake6");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.query(card).getText()).as("the query is kept").isEqualTo("row");
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(30);
            }
        });
    }

    @Test void routerOpensTheModeListRemovesChipsWithBackspaceAndRunsTheSecondVerbWithCmdEnter() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner);
                var fake = new FakeScope(); owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                int mod = primary(true);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_P, mod | InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_P));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).as("claimed, ignored").isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_BACK_SPACE, 0)))
                    .as("no chip: the field keeps Backspace").isFalse();
                owner.commandPalette().open("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_BACK_SPACE, 0))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_BACK_SPACE));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                owner.commandPalette().open("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(fake.executed).containsExactly("alpha:two");
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_P, mod | InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_P));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_P, mod | InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(owner.commandPalette().isOpen()).as("Shift+Cmd+P with no chip closes").isFalse();
            }
        });
    }

    @Test void aStepReplacesTheListCompletesWithValuesAndReopensWhereTheScopeAsks() throws Exception {
        edt(() -> {
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new java.util.ArrayDeque<>()), DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                ThemeTestSupport.controller(false),
                dev.jasper.app.config.KeyBindings.defaults(true), new dev.jasper.app.commands.CommandHistory(), true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                var router = PaletteKeyRouterTest.router(owner, true, root);
                palette.open("test.fake");
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("al");
                assertThat(PaletteTestSupport.hintActions(card)).containsExactly("One ⏎", "Two ⌘⏎", "Three ⇧⏎");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(palette.stepOpen()).isTrue();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.query(card).getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.stepFields(card)).hasSize(2);
                assertThat(PaletteTestSupport.stepFields(card).getFirst().getText()).isEqualTo("pre");
                assertThat(PaletteTestSupport.stepTitle(card)).isEqualTo("Fill Alpha");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_2, primary(true)))).isTrue();
                assertThat(fake.executed).isEmpty();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                assertThat(PaletteTestSupport.stepFocusIndex(card)).isEqualTo(1);
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(PaletteTestSupport.stepFocusIndex(card)).isZero();
                assertThat(palette.activeScopeId()).as("Tab moves between fields in a step").isEqualTo("test.fake");
                PaletteTestSupport.stepFields(card).get(1).setText("two");
                fake.stepResult = PaletteStep.Result.error("Nope");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(fake.completed).hasSize(1);
                assertThat(fake.completed.getFirst()).containsEntry("first", "pre").containsEntry("second", "two");
                assertThat(palette.stepOpen()).isTrue();
                assertThat(PaletteTestSupport.stepError(card)).isEqualTo("Nope");
                fake.stepResult = PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab");
                palette.enterPressed(1);
                assertThat(fake.completed).hasSize(2);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.selectedRow(card).id()).isEqualTo("new_tab");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.query(card).getText()).isEmpty();
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    @Test void escapeLeavesAStepWithTheQueryIntactAndScopeShortcutsOrDoneDismissIt() throws Exception {
        edt(() -> {
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new java.util.ArrayDeque<>()), DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                ThemeTestSupport.controller(false),
                dev.jasper.app.config.KeyBindings.defaults(true), new dev.jasper.app.commands.CommandHistory(), true)) {
                install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("al");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).isTrue();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("alpine", "Alpine"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(palette.stepOpen()).isTrue();
                palette.escape();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.query(card).getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(2);
                palette.enterPressed(2);
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.query(card).getText()).isEqualTo("al");
                palette.open("test.fake");
                palette.enterPressed(2);
                fake.stepResult = PaletteStep.Result.done();
                palette.enterPressed(0);
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.completed).hasSize(1);
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    @Test void cmdShiftEnterIsNotTheThirdVerb() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                owner.commandPalette().open("test.fake");
                router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, primary(true) | InputEvent.SHIFT_DOWN_MASK));
                assertThat(owner.commandPalette().stepOpen()).isFalse();
                assertThat(fake.executed).isEmpty();
                assertThat(owner.commandPalette().isOpen()).isTrue();
            }
        });
    }

    @Test void aSecondEnterWhileAStepsAsyncCompletionIsPendingDoesNothing() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette();
                palette.open("test.fake");
                fake.deferCompletion = true;
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).isTrue();
                palette.enterPressed(0);
                palette.enterPressed(0);
                assertThat(fake.completed).hasSize(1);
                assertThat(palette.stepOpen()).isTrue();
                assertThat(palette.isOpen()).isTrue();
                fake.pending.accept(PaletteStep.Result.done());
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void queuedCompletionCannotAffectAnotherOpeningEvenWhenScopeReusesItsStep() throws Exception {
        for (int transition = 0; transition < 3; transition++) {
            final int mode = transition;
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowContent>();
            var fake = new FakeScope(); fake.deferCompletion = true; fake.reuseStep = true;
            var delivered = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                edt(() -> {
                    var content = owner(true); ref.set(content); install(content);
                    var registration = content.scopes().register(fake);
                    var palette = content.commandPalette();
                    palette.open(fake.id()); palette.enterPressed(2); palette.enterPressed(0);
                    assertThat(fake.pending).isNotNull();
                    var oldCompletion = fake.pending;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        delivered.set(true);
                        oldCompletion.accept(PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab"));
                    });
                    if (mode == 0) palette.dismiss();
                    else if (mode == 1) { registration.close(); content.scopes().register(fake); }
                    else {
                        var oldTab = content.currentTab();
                        content.newTab(DesktopTestSupport.HOME); content.closeTab(oldTab);
                    }
                    palette.open(fake.id()); dev.jasper.app.palette.PaletteTestSupport.query(palette.component()).setText("beta");
                    palette.enterPressed(2); palette.enterPressed(0);
                    assertThat(fake.completed).hasSize(2);
                });
                edt(() -> {
                    assertThat(delivered).isTrue();
                    var palette = ref.get().commandPalette();
                    assertThat(palette.isOpen()).as("transition %s", mode).isTrue();
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                    assertThat(dev.jasper.app.palette.PaletteTestSupport.query(palette.component()).getText()).isEqualTo("beta");
                    palette.enterPressed(0);
                    assertThat(fake.completed).hasSize(2);
                    assertThat(fake.executed).isEmpty();
                });
            } finally { edt(() -> { if (ref.get() != null) ref.get().close(); }); }
        }
    }
}
