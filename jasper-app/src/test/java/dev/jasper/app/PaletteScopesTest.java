package dev.jasper.app;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.edt;
import static dev.jasper.app.CommandPaletteShortcutsTest.*;

class PaletteScopesTest {
    /** A scope with no Swing in it: rows and verbs are data; execution records what it was asked to do. */
    static final class FakeScope implements PaletteScope {
        final List<String> executed = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();
        int activations;
        List<PaletteRow> rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"));
        PaletteStep.Result stepResult = PaletteStep.Result.done();
        final List<Map<String, String>> completed = new ArrayList<>();
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
            return new PaletteStep("Fill " + row.title(),
                List.of(new PaletteStep.Field("first", "First", "pre"), new PaletteStep.Field("second", "Second", "")),
                (values, done) -> { completed.add(values); done.accept(stepResult); });
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
        @Override public CommandRegistry.Subscription onChanged(Runnable listener) {
            listeners.add(listener);
            return new CommandRegistry.Subscription(() -> listeners.remove(listener));
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
                assertThat(card.chip().getText()).isEqualTo("Commands");
                assertThat(card.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Type a command, or > to switch scope");
                card.queryField().setText(">");
                assertThat(palette.pickerOpen()).isTrue();
                assertThat(card.sectionLabel().getText()).isEqualTo("Scopes");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(2);
                assertThat(card.resultList().getModel().getElementAt(0).tag()).isEqualTo("⌘K");
                card.queryField().setText(">fx");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(1);
                assertThat(card.resultList().getSelectedValue().id()).isEqualTo("test.fake");
                assertThat(card.resultList().getSelectedValue().detail()).isEqualTo("Fixture scope");
                assertThat(palette.tabPressed()).isTrue();
                assertThat(palette.pickerOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).isEmpty();
                assertThat(card.chip().getText()).isEqualTo("Fake");
                assertThat(card.footer().isVisible()).isTrue();
                assertThat(card.sectionLabel().getText()).isEqualTo("Most recent");
                assertThat(fake.activations).isEqualTo(1);
                card.queryField().setText("bet");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(1);
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.queryField().getText()).isEqualTo("bet");
                assertThat(card.footer().isVisible()).isFalse();
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
                card.executeSelected();
                assertThat(palette.isOpen()).isFalse();
                palette.open("test.fake");
                card.executeSelected(1);
                palette.open("test.fake");
                card.executeNumber(2);
                assertThat(fake.executed).containsExactly("alpha:one:sh", "alpha:two:sh", "beta:one:sh");
                palette.toggle();
                card.queryField().setText("new tab");
                card.executeSelected(1);
                assertThat(palette.isOpen()).isTrue();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
                card.queryField().setText(">");
                card.executeSelected();
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
                card.selectRelative(1);
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(card.resultList().getModel().getSize()).isEqualTo(3);
                assertThat(card.resultList().getSelectedValue().id()).isEqualTo("beta");
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
                assertThat(card.footer().getText()).isEqualTo("⏎ One  ⌘⏎ Two  ⇧⏎ Three");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(palette.stepOpen()).isTrue();
                assertThat(card.stepFields()).hasSize(2);
                assertThat(card.stepFields().getFirst().getText()).isEqualTo("pre");
                assertThat(card.sectionLabel().getText()).isEqualTo("Fill Alpha");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_2, primary(true)))).isTrue();
                assertThat(fake.executed).isEmpty();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                assertThat(card.stepFocusIndex()).isEqualTo(1);
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(card.stepFocusIndex()).isZero();
                card.stepFields().get(1).setText("two");
                fake.stepResult = PaletteStep.Result.error("Nope");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(fake.completed).hasSize(1);
                assertThat(fake.completed.getFirst()).containsEntry("first", "pre").containsEntry("second", "two");
                assertThat(palette.stepOpen()).isTrue();
                assertThat(card.stepError().getText()).isEqualTo("Nope");
                fake.stepResult = PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab");
                palette.enterPressed(1);
                assertThat(fake.completed).hasSize(2);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.resultList().getSelectedValue().id()).isEqualTo("new_tab");
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
                assertThat(card.resultList().getModel().getSize()).isEqualTo(2);
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
}
