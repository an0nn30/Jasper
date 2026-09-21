package dev.jasper.app.workspace;

import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.commands.CommandSearch;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.workspace.PaletteKeyRouterTest;
import dev.jasper.app.palette.PaletteResults;
import dev.jasper.app.palette.PaletteRow;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteStep;
import dev.jasper.app.palette.PaletteVerb;
import dev.jasper.app.lifecycle.Subscription;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.CommandPaletteShortcutsTest.*;

class PaletteScopesTest {
    /** A scope with no Swing in it: rows and verbs are data; execution records what it was asked to do. */
    static final class FakeScope implements PaletteScope {
        final List<String> executed = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();
        int activations;
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
        @Override public List<String> aliases() { return List.of("fx"); }
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
                .filter(row -> q.isEmpty() || row.title().toLowerCase(Locale.ROOT).contains(q)).toList(),
                q.isEmpty() ? "Most recent" : null, null);
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
            executed.add(row.id() + ":" + verb.id() + ":" + context.target().shellName().get());
        }
        @Override public Subscription onChanged(Runnable listener) {
            listeners.add(listener);
            return new Subscription(() -> listeners.remove(listener));
        }
    }

    @Test void pickerFiltersByAliasTabCommitsAndShortcutSwitchKeepsTheQuery() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.chip(card).getText()).isEqualTo("Commands");
                assertThat(card.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Type a command, or > to switch scope");
                card.queryField().setText(">");
                assertThat(palette.pickerOpen()).isTrue();
                assertThat(PaletteTestSupport.sectionLabel(card).getText()).isEqualTo("Scopes");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getSize()).isEqualTo(2);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getElementAt(0).tag()).isEqualTo("⌘K");
                card.queryField().setText(">fx");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getSize()).isEqualTo(1);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getSelectedValue().id()).isEqualTo("test.fake");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getSelectedValue().detail()).isEqualTo("Fixture scope");
                assertThat(palette.tabPressed()).isTrue();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).isEmpty();
                assertThat(PaletteTestSupport.chip(card).getText()).isEqualTo("Fake");
                assertThat(PaletteTestSupport.footer(card).isVisible()).isTrue();
                assertThat(PaletteTestSupport.sectionLabel(card).getText()).isEqualTo("Most recent");
                assertThat(fake.activations).isEqualTo(1);
                card.queryField().setText("bet");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getSize()).isEqualTo(1);
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.queryField().getText()).isEqualTo("bet");
                assertThat(PaletteTestSupport.footer(card).isVisible()).isFalse();
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.isOpen()).isFalse();
                palette.open("test.missing");
                assertThat(palette.isOpen()).isFalse();
                assertThat(palette.tabPressed()).isFalse();
            }
        });
    }

    @Test void greaterThanOnlyOpensThePickerAtTheStartOfAnEmptyQueryAndEscapeLeavesIt() throws Exception {
        edt(() -> {
            try (var owner = owner(false)) {
                install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText("a");
                card.queryField().setText("a>");
                assertThat(palette.pickerOpen()).isFalse();
                card.queryField().setText("");
                card.queryField().setText(">");
                assertThat(palette.pickerOpen()).isTrue();
                card.queryField().setText("");
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                palette.openPicker();
                assertThat(palette.pickerOpen()).isTrue();
                palette.escape();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(card.queryField().getText()).isEmpty();
                palette.escape();
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void verbsRouteEnterCmdEnterAndNumbersThroughTheOriginTarget() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(card);
                assertThat(palette.isOpen()).isFalse();
                palette.open("test.fake");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(card, 1);
                palette.open("test.fake");
                PaletteTestSupport.executeNumber(card, 2);
                assertThat(fake.executed).containsExactly("alpha:one:sh", "alpha:two:sh", "beta:one:sh");
                palette.toggle();
                card.queryField().setText("new tab");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(card, 1);
                assertThat(palette.isOpen()).isTrue();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
                card.queryField().setText(">");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(card);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
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
                PaletteTestSupport.selectRelative(card, 1);
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getSize()).isEqualTo(3);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getSelectedValue().id()).isEqualTo("beta");
                registration.close();
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void theActiveScopesShortcutDismissesEvenWhileThePickerIsOpen() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText(">");
                assertThat(palette.pickerOpen()).isTrue();
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void switchingScopesFromThePickerDropsTheRawFilterText() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText(">fa");
                assertThat(palette.pickerOpen()).isTrue();
                palette.open("test.fake");
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(card.queryField().getText()).isEmpty();
            }
        });
    }

    @Test void routerRepeatingTheOpenShortcutDismissesEvenWithThePickerFilteredMidway() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                int mod = primary(true);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.typed(owner, 'k'))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isTrue();
                owner.commandPalette().component().queryField().setText(">");
                assertThat(owner.commandPalette().pickerOpen()).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.typed(owner, 'k'))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }

    @Test void routerOpensSwitchesAndCommitsWithTabAndCmdEnter() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                int mod = primary(true);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                owner.commandPalette().component().queryField().setText(">fa");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, mod))).isTrue();
                assertThat(fake.executed).containsExactly("alpha:two:sh");
                assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }

    @Test void aStepReplacesTheListCompletesWithValuesAndReopensWhereTheScopeAsks() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                var router = PaletteKeyRouterTest.router(owner, true, root);
                palette.open("test.fake");
                card.queryField().setText("al");
                assertThat(PaletteTestSupport.footer(card).getText()).isEqualTo("⏎ One  ⌘⏎ Two  ⇧⏎ Three");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(palette.stepOpen()).isTrue();
                palette.openPicker();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(card.queryField().getText()).isEqualTo("al");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.stepFields(card)).hasSize(2);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.stepFields(card).getFirst().getText()).isEqualTo("pre");
                assertThat(PaletteTestSupport.sectionLabel(card).getText()).isEqualTo("Fill Alpha");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_2, primary(true)))).isTrue();
                assertThat(fake.executed).isEmpty();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                assertThat(PaletteTestSupport.stepFocusIndex(card)).isEqualTo(1);
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(PaletteTestSupport.stepFocusIndex(card)).isZero();
                dev.jasper.app.palette.PaletteTestSupport.stepFields(card).get(1).setText("two");
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
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getSelectedValue().id()).isEqualTo("new_tab");
                assertThat(card.queryField().getText()).isEmpty();
            }
        });
    }

    @Test void escapeLeavesAStepWithTheQueryIntactAndScopeShortcutsOrDoneDismissIt() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
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
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getSize()).isEqualTo(2);
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
            }
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
