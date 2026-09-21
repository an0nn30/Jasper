package dev.jasper.app;

import java.awt.*;
import java.awt.event.*;
import java.text.AttributedString;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.*;
import static dev.jasper.app.CommandPaletteShortcutsTest.*;

class PaletteKeyRouterTest {
    @Test void openingRepeatAndClosingOwnCompleteSequencesOnBothPlatforms() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            try (var owner = owner(mac)) {
                var root = install(owner); var router = router(owner, mac, root);
                int mod = primary(mac);
                assertThat(router.dispatch(press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(router.dispatch(press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(router.dispatch(typed(owner, 'k'))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isTrue();
                assertThat(router.dispatch(release(owner, KeyEvent.VK_K))).isTrue();
                assertThat(router.drained()).isTrue();
                assertThat(router.dispatch(press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(typed(owner, 'k'))).isTrue();
                assertThat(router.dispatch(release(owner, KeyEvent.VK_K))).isTrue();
                assertThat(router.dispatch(press(owner, KeyEvent.VK_A, 0))).isFalse();
                assertThat(router.dispatch(typed(owner, 'a'))).isFalse();
                assertThat(router.dispatch(release(owner, KeyEvent.VK_A))).isFalse();
            }
        });
    }

    @Test void executionRepeatsAndForeignFocusTailsAreConsumedButNewForeignKeysPass() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            try (var owner = owner(mac); var other = owner(mac)) {
                var root = install(owner); install(other); var router = router(owner, mac, root);
                var count = new AtomicInteger();
                owner.commands().register(command("fixture", count::incrementAndGet));
                owner.commandPalette().toggle(); owner.commandPalette().component().queryField().setText("fixture");
                assertThat(router.dispatch(press(owner, KeyEvent.VK_1, primary(mac)))).isTrue();
                assertThat(count.get()).isEqualTo(1);
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(press(other, KeyEvent.VK_1, primary(mac)))).isTrue();
                assertThat(router.dispatch(typed(other, '1'))).isTrue();
                assertThat(router.dispatch(release(other, KeyEvent.VK_1))).isTrue();
                assertThat(count.get()).isEqualTo(1);
                assertThat(router.dispatch(press(other, KeyEvent.VK_K, primary(mac)))).isFalse();
                assertThat(router.dispatch(typed(other, 'k'))).isFalse();
                assertThat(router.dispatch(release(other, KeyEvent.VK_K))).isFalse();
            }
        });
    }

    @Test void interveningForeignPressOwnsItsTypedEventWhileOlderTailStillDrains() throws Exception {
        edt(() -> {
            try (var owner = owner(true); var other = owner(true)) {
                var router = router(owner, true, install(owner));
                router.dispatch(press(owner, KeyEvent.VK_K, primary(true)));
                assertThat(router.dispatch(press(other, KeyEvent.VK_A, 0))).isFalse();
                assertThat(router.dispatch(typed(other, 'a'))).isFalse();
                assertThat(router.dispatch(release(other, KeyEvent.VK_A))).isFalse();
                assertThat(router.drained()).isFalse();
                assertThat(router.dispatch(press(other, KeyEvent.VK_K, primary(true)))).isTrue();
                assertThat(router.dispatch(typed(other, 'k'))).isTrue();
                assertThat(router.dispatch(release(other, KeyEvent.VK_K))).isTrue();
            }
        });
    }

    @Test void liveRemappingAndNoneChangeOpeningAndPreserveNativeCopyPaste() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner); KeyBindings[] keys = {KeyBindings.defaults(true)};
                var router = new PaletteKeyRouter(owner.commandPalette().controller(), owner.commandPalette()::open, () -> keys[0], true,
                    source -> SwingUtilities.isDescendingFrom(source, root));
                keys[0] = KeyBindings.withOverrides(true, Map.of("command_palette", "alt+p"));
                assertThat(router.dispatch(press(owner, KeyEvent.VK_K, primary(true)))).isFalse();
                assertThat(router.dispatch(press(owner, KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK))).isTrue();
                router.dispatch(release(owner, KeyEvent.VK_P));
                var field = owner.commandPalette().component().queryField();
                for (int code : new int[]{KeyEvent.VK_C, KeyEvent.VK_V}) {
                    assertThat(router.dispatch(press(field, code, primary(true)))).isFalse();
                    assertThat(router.dispatch(release(field, code))).isFalse();
                }
                owner.commandPalette().dismiss(); keys[0] = KeyBindings.withOverrides(true, Map.of("command_palette", "none"));
                assertThat(router.dispatch(press(owner, KeyEvent.VK_K, primary(true)))).isFalse();
                assertThat(router.dispatch(press(owner, KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK))).isFalse();
            }
        });
    }

    @Test void missingNumberNavigationStaleResultAndImePreserveEditorOwnership() throws Exception {
        edt(() -> {
            try (var owner = owner(false)) {
                var root = install(owner); var router = router(owner, false, root);
                var count = new AtomicInteger(); var first = command("fixture.one", count::incrementAndGet);
                var second = command("fixture.two", count::incrementAndGet);
                var registration = owner.commands().register(first); owner.commands().register(second);
                owner.commandPalette().toggle(); var card = owner.commandPalette().component();
                card.queryField().setText("fixture");
                for (int i = 0; i < 6; i++) assertThat(router.dispatch(press(card.queryField(), KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(card.resultList().getSelectedIndex()).isEqualTo(1);
                for (int i = 0; i < 6; i++) router.dispatch(press(card.queryField(), KeyEvent.VK_UP, 0));
                assertThat(card.resultList().getSelectedIndex()).isZero();
                assertThat(router.dispatch(press(card.queryField(), KeyEvent.VK_5, primary(false)))).isTrue();
                assertThat(router.dispatch(typed(card.queryField(), '5'))).isTrue();
                router.dispatch(release(card.queryField(), KeyEvent.VK_5));
                assertThat(count.get()).isZero(); assertThat(owner.commandPalette().isOpen()).isTrue();
                assertThat(router.dispatch(press(card.queryField(), KeyEvent.VK_1, 0))).isFalse();
                assertThat(router.dispatch(typed(card.queryField(), '1'))).isFalse();
                var ime = new InputMethodEvent(card.queryField(), InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    new AttributedString("pending").getIterator(), 0, null, null);
                for (var listener : card.queryField().getInputMethodListeners()) listener.inputMethodTextChanged(ime);
                for (int code : new int[]{KeyEvent.VK_ENTER, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_A})
                    assertThat(router.dispatch(press(card.queryField(), code, 0))).isFalse();
                assertThat(count.get()).isZero();
                assertThat(router.dispatch(press(card.queryField(), KeyEvent.VK_ESCAPE, 0))).isTrue();
                router.dispatch(release(card.queryField(), KeyEvent.VK_ESCAPE));
                var committed = new InputMethodEvent(card.queryField(), InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    null, 0, null, null);
                for (var listener : card.queryField().getInputMethodListeners()) listener.inputMethodTextChanged(committed);
                owner.commandPalette().toggle(); registration.close();
                card.setResults(owner.commandsScope().rows(List.of(first)), null, null);
                assertThat(router.dispatch(press(card.queryField(), KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(router.dispatch(press(card.queryField(), KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(count.get()).isZero();
                router.dispatch(release(card.queryField(), KeyEvent.VK_ENTER));
                owner.commandPalette().toggle(); second.action().setEnabled(false);
                card.setResults(owner.commandsScope().rows(List.of(second)), null, null);
                router.dispatch(press(card.queryField(), KeyEvent.VK_ENTER, 0));
                assertThat(count.get()).isZero();
            }
        });
    }

    @Test void appShortcutSequenceIsSuppressedWhileOpenWithoutLeakingItsText() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            try (var owner = owner(mac)) {
                var router = router(owner, mac, install(owner)); owner.commandPalette().toggle();
                var shortcut = KeyBindings.defaults(mac).strokeFor(ActionId.NEW_TAB).orElseThrow();
                assertThat(router.dispatch(press(owner, shortcut.getKeyCode(), shortcut.getModifiers()))).isTrue();
                assertThat(router.dispatch(typed(owner, 't'))).isTrue();
                assertThat(router.dispatch(release(owner, shortcut.getKeyCode()))).isTrue();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
                assertThat(owner.commandPalette().isOpen()).isTrue();
            }
        });
    }

    @Test void deactivationAndClosePreserveTailAndResetCancelsIt() throws Exception {
        edt(() -> {
            try (var owner = owner(true); var other = owner(true)) {
                var root = install(owner); var router = router(owner, true, root);
                router.dispatch(press(owner, KeyEvent.VK_K, primary(true))); owner.setActive(false);
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.drained()).isFalse(); router.close();
                assertThat(router.dispatch(press(other, KeyEvent.VK_A, 0))).isFalse();
                assertThat(router.dispatch(typed(other, 'a'))).isFalse();
                assertThat(router.dispatch(press(other, KeyEvent.VK_K, primary(true)))).isTrue();
                assertThat(router.dispatch(typed(other, 'k'))).isTrue();
                assertThat(router.dispatch(release(other, KeyEvent.VK_K))).isTrue();
                assertThat(router.drained()).isTrue();
                owner.setActive(true); assertThat(router.dispatch(press(owner, KeyEvent.VK_K, primary(true)))).isFalse();
                router = router(owner, true, root); router.dispatch(press(owner, KeyEvent.VK_K, primary(true)));
                router.reset(); assertThat(router.drained()).isTrue();
                assertThat(router.dispatch(typed(owner, 'k'))).isFalse();
            }
        });
    }

    static PaletteKeyRouter router(WindowContent owner, boolean mac, JRootPane root) {
        return new PaletteKeyRouter(owner.commandPalette().controller(), owner.commandPalette()::open, () -> KeyBindings.defaults(mac), mac,
            source -> SwingUtilities.isDescendingFrom(source, root));
    }
    static Command command(String id, Runnable run) {
        return new Command(id, new AbstractAction(id) {
            @Override public void actionPerformed(ActionEvent e) { run.run(); }
        }, List.of());
    }
    static KeyEvent press(Component source, int code, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }
    static KeyEvent typed(Component source, char character) {
        return new KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, character);
    }
    static KeyEvent release(Component source, int code) {
        return new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
    }
}
