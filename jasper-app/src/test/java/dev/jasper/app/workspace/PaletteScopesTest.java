package dev.jasper.app.workspace;

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

    @Test void theCommandPaletteShortcutOpensAllWithATabPerScope() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.tabIds(card)).containsExactly(PaletteScope.ALL_ID, PaletteScope.COMMANDS_ID, "test.fake");
                assertThat(PaletteTestSupport.selectedTabId(card)).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Search everywhere");
                var entries = PaletteTestSupport.entries(card);
                assertThat(entries.getFirst()).isEqualTo("# Commands");
                assertThat(entries).containsSubsequence("# Fake", "test.fake/alpha", "test.fake/beta");
                assertThat(fake.activations).isEqualTo(1);
                assertThat(PaletteTestSupport.selectedRow(card)).isNotNull();
                palette.toggle();
                assertThat(palette.isOpen()).as("the open tab's own shortcut closes").isFalse();
            }
        });
    }

    @Test void greaterThanInAllIsAPlainQueryNotAPicker() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("gt", "closes > right"));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                var tabsBefore = PaletteTestSupport.tabIds(card);
                var selectedBefore = PaletteTestSupport.selectedTabId(card);
                card.queryField().setText(">");
                assertThat(card.queryField().getText()).as("no picker rewrites the field").isEqualTo(">");
                assertThat(PaletteTestSupport.tabIds(card)).as("the tabs are unchanged").isEqualTo(tabsBefore);
                assertThat(PaletteTestSupport.selectedTabId(card)).as("the selected tab is unchanged").isEqualTo(selectedBefore);
                assertThat(PaletteTestSupport.entries(card)).as("scopes are searched with the literal '>' text")
                    .contains("test.fake/gt");
            }
        });
    }

    @Test void allCapsEachSectionAndOffersTheRestInTheScopesTab() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("alpine", "Alpine"), PaletteRow.of("alps", "Alps"));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.setMaxResults(2);
                palette.toggle();
                card.queryField().setText("alp");
                var entries = PaletteTestSupport.entries(card);
                int section = entries.indexOf("# Fake");
                assertThat(entries.subList(section, section + 4)).containsExactly("# Fake", "test.fake/alpha", "test.fake/alpine", "more:test.fake");
                PaletteTestSupport.selectRow(card, "alpine");
                palette.moveSelection(1);
                assertThat(PaletteTestSupport.selectedRow(card)).as("the More row is selected").isNull();
                palette.enterPressed(0);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).as("the query is kept").isEqualTo("alp");
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(2);
                assertThat(fake.executed).isEmpty();
            }
        });
    }

    @Test void aScopeThatOptsOutKeepsItsTabButStaysOutOfAll() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope(); fake.inAll = false;
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(PaletteTestSupport.tabIds(card)).contains("test.fake");
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
                assertThat(palette.isOpen()).isTrue();
                assertThat(PaletteTestSupport.entries(card)).contains("# Commands", "# Fake").doesNotContain("# Broken");
            }
        });
    }

    @Test void tabAndShiftTabCycleTheTabsAndKeepTheQuery() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText("be");
                assertThat(palette.tabPressed(false)).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Type a command");
                assertThat(palette.tabPressed(false)).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).isEqualTo("be");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("test.fake/beta");
                palette.tabPressed(false);
                assertThat(palette.activeScopeId()).as("wraps to All").isEqualTo(PaletteScope.ALL_ID);
                palette.tabPressed(true);
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                PaletteTestSupport.clickTab(card, PaletteScope.COMMANDS_ID);
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.selectedTabId(card)).isEqualTo(PaletteScope.COMMANDS_ID);
                palette.dismiss();
                assertThat(palette.tabPressed(false)).as("closed").isFalse();
            }
        });
    }

    @Test void upAndDownSkipSectionHeadersAcrossScopes() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                int commands = PaletteTestSupport.entries(card).indexOf("# Fake") - 1;
                assertThat(commands).isPositive();
                palette.moveSelection(commands - 1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isEqualTo(commands - 1);
                palette.moveSelection(1);
                assertThat(PaletteTestSupport.selectedRow(card).id()).as("past the Fake header").isEqualTo("alpha");
                palette.moveSelection(-1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isEqualTo(commands - 1);
                palette.moveSelection(50);
                assertThat(PaletteTestSupport.selectedRow(card).id()).isEqualTo("beta");
            }
        });
    }

    @Test void theHintBarShowsTheRowsDetailAndItsScopesOtherVerbsWhichAreClickable() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(new PaletteRow("alpha", "Alpha", "alpha detail", "tag", null, true, null));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.hint(card)).isEqualTo("alpha detail");
                assertThat(PaletteTestSupport.hintActions(card)).containsExactly("Two ⌘⏎", "Three ⇧⏎");
                PaletteTestSupport.clickHintAction(card, 0);
                assertThat(fake.executed).containsExactly("alpha:two");
                palette.open("test.fake");
                PaletteTestSupport.clickHintAction(card, 1);
                assertThat(palette.stepOpen()).isTrue();
                assertThat(PaletteTestSupport.hintBar(card).isVisible()).as("hidden in a step").isFalse();
                palette.escape();
                assertThat(PaletteTestSupport.hintBar(card).isVisible()).isTrue();
                palette.open(PaletteScope.COMMANDS_ID);
                card.queryField().setText("new tab");
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
                card.queryField().setText("new tab");
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
                PaletteTestSupport.selectRow(card, "beta");
                PaletteTestSupport.executeSelected(card, 1);
                assertThat(fake.executed).containsExactly("beta:two");
                palette.toggle();
                PaletteTestSupport.selectRow(card, "beta");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).as("the scope's own step").isTrue();
                assertThat(PaletteTestSupport.stepTitle(card).getText()).isEqualTo("Fill Beta");
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

    @Test void scopesRegisteredOrRemovedWhileAllIsOpenUpdateItsTabsAndSections() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                assertThat(PaletteTestSupport.tabIds(card)).contains("test.fake");
                assertThat(PaletteTestSupport.entries(card)).contains("# Fake");
                fake.rows = List.of(PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(PaletteTestSupport.entries(card)).contains("test.fake/gamma");
                registration.close();
                assertThat(palette.isOpen()).as("All stays open").isTrue();
                assertThat(PaletteTestSupport.tabIds(card)).doesNotContain("test.fake");
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("# Fake");
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void allBudgetsItsRowsAcrossManyScopesInsteadOfThrowing() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                // Eleven scopes at the settings' own max_results cap (20) would ask for 220 item rows,
                // over PaletteResults.MAX_ROWS (200); All must budget instead of handing setEntries too many.
                for (int n = 0; n < 11; n++) {
                    int index = n;
                    var scope = new FakeScope() {
                        @Override public String id() { return "test.fake" + index; }
                        @Override public String label() { return "Fake" + index; }
                    };
                    var rows = new ArrayList<PaletteRow>();
                    for (int r = 0; r < 25; r++) rows.add(PaletteRow.of("row" + r, "Row " + r));
                    scope.rows = rows;
                    owner.scopes().register(scope);
                }
                var palette = owner.commandPalette(); var card = palette.component();
                palette.setMaxResults(20);
                palette.toggle();
                assertThat(palette.isOpen()).as("All opens without throwing").isTrue();
                assertThat(PaletteTestSupport.rowCount(card)).as("at most PaletteResults.MAX_ROWS (200) item rows").isEqualTo(200);
                var headers = PaletteTestSupport.entries(card).stream().filter(entry -> entry.startsWith("# Fake")).toList();
                var expected = new ArrayList<String>();
                for (int n = 0; n < 10; n++) expected.add("# Fake" + n);
                assertThat(headers).as("sections in tab order, stopping once the budget is spent").containsExactlyElementsOf(expected);
            }
        });
    }

    @Test void routerOpensAllCyclesWithTabAndRunsTheSecondVerbWithCmdEnter() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner);
                var fake = new FakeScope(); owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                int mod = primary(true);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                for (int i = 0; i < 2; i++) {
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                    router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                }
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                owner.commandPalette().open("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(fake.executed).containsExactly("alpha:two");
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(owner.commandPalette().isOpen()).as("Cmd+K on All closes").isFalse();
            }
        });
    }

    @Test void aStepReplacesTheListCompletesWithValuesAndReopensWhereTheScopeAsks() throws Exception {
        edt(() -> {
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new java.util.ArrayDeque<>()), DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.Appearance.LIGHT),
                dev.jasper.app.config.KeyBindings.defaults(true), new dev.jasper.app.commands.CommandHistory(), true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                var router = PaletteKeyRouterTest.router(owner, true, root);
                palette.open("test.fake");
                card.queryField().setText("al");
                assertThat(PaletteTestSupport.hintActions(card)).containsExactly("Two ⌘⏎", "Three ⇧⏎");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(palette.stepOpen()).isTrue();
                assertThat(card.queryField().getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.stepFields(card)).hasSize(2);
                assertThat(PaletteTestSupport.stepFields(card).getFirst().getText()).isEqualTo("pre");
                assertThat(PaletteTestSupport.stepTitle(card).getText()).isEqualTo("Fill Alpha");
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
                assertThat(PaletteTestSupport.stepError(card).getText()).isEqualTo("Nope");
                fake.stepResult = PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab");
                palette.enterPressed(1);
                assertThat(fake.completed).hasSize(2);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.selectedRow(card).id()).isEqualTo("new_tab");
                assertThat(card.queryField().getText()).isEmpty();
            } finally { new dev.jasper.app.appearance.ThemeController(); }
        });
    }

    @Test void escapeLeavesAStepWithTheQueryIntactAndScopeShortcutsOrDoneDismissIt() throws Exception {
        edt(() -> {
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new java.util.ArrayDeque<>()), DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.Appearance.LIGHT),
                dev.jasper.app.config.KeyBindings.defaults(true), new dev.jasper.app.commands.CommandHistory(), true)) {
                install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                card.queryField().setText("al");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).isTrue();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("alpine", "Alpine"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(palette.stepOpen()).isTrue();
                palette.escape();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(card.queryField().getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(2);
                palette.enterPressed(2);
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.queryField().getText()).isEqualTo("al");
                palette.open("test.fake");
                palette.enterPressed(2);
                fake.stepResult = PaletteStep.Result.done();
                palette.enterPressed(0);
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.completed).hasSize(1);
            } finally { new dev.jasper.app.appearance.ThemeController(); }
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
                    palette.open(fake.id()); palette.component().queryField().setText("beta");
                    palette.enterPressed(2); palette.enterPressed(0);
                    assertThat(fake.completed).hasSize(2);
                });
                edt(() -> {
                    assertThat(delivered).isTrue();
                    var palette = ref.get().commandPalette();
                    assertThat(palette.isOpen()).as("transition %s", mode).isTrue();
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                    assertThat(palette.component().queryField().getText()).isEqualTo("beta");
                    palette.enterPressed(0);
                    assertThat(fake.completed).hasSize(2);
                    assertThat(fake.executed).isEmpty();
                });
            } finally { edt(() -> { if (ref.get() != null) ref.get().close(); }); }
        }
    }
}
