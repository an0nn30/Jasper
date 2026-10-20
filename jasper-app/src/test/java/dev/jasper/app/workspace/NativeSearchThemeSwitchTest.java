package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.commands.ActionId;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A live chrome theme switch through the real window refresh keeps the focused find query's text, caret, selection,
 * document and focus ownership, and the title search field's context. Headless limits: there is no native window, so
 * focus is the KeyboardFocusManager's global owner (set as a focused field would set it); the test proves the switch
 * neither replaces, reparents nor refocuses either control, not that the operating system keeps keyboard focus.
 * The title search field is deliberately non-focusable with no editable text (the shortcut is its keyboard route).
 */
@DisabledOnOs(OS.WINDOWS)
class NativeSearchThemeSwitchTest {
    /** Lets a headless test make a component the global focus owner, as a real focus gain would. */
    private static final class Focus extends DefaultKeyboardFocusManager {
        void focus(Component component) { setGlobalFocusOwner(component); setGlobalPermanentFocusOwner(component); }
    }

    @AfterEach void reset() throws Exception {
        edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(null));
        closeOwners();
        edt(() -> ThemeTestSupport.controller(true));
    }

    @Test void focusedFindQueryAndTitleSearchSurviveALiveChromeThemeSwitch() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var themes = new ThemeController[1];
        var owner = new WindowContent[1];
        edt(() -> { themes[0] = ThemeTestSupport.controller(true); owner[0] = content(launcher(pending), themes[0]); });
        pending.remove().run();
        var focus = new Focus();
        edt(() -> {
            var pane = owner[0].currentPane();
            owner[0].invoke(ActionId.FIND);
            var bar = pane.findBar();
            var query = bar.queryField();
            var search = owner[0].chrome().search();
            search.setContext("~/projects/jasper", "Working directory", null);
            search.setCollapsed(false);
            query.setText("pending query");
            query.select(2, 7);
            var document = query.getDocument();
            var parent = query.getParent();
            var searchParent = search.getParent();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
            focus.focus(query);
            assertThat(query.isFocusOwner()).as("precondition: the query owns focus").isTrue();
            assertThat(query.getBackground()).isEqualTo(ThemeTestSupport.generated(true).semantic().color(SemanticColor.SURFACE_INPUT));

            themes[0].configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH);

            var light = ThemeTestSupport.generated(false).semantic();
            assertThat(pane.findBar()).isSameAs(bar);
            assertThat(bar.queryField()).isSameAs(query);
            assertThat(query.getParent()).isSameAs(parent);
            assertThat(SwingUtilities.isDescendingFrom(query, owner[0])).isTrue();
            assertThat(query.getDocument()).isSameAs(document);
            assertThat(query.getText()).isEqualTo("pending query");
            assertThat(query.getSelectionStart()).isEqualTo(2);
            assertThat(query.getSelectionEnd()).isEqualTo(7);
            assertThat(query.getCaretPosition()).isEqualTo(7);
            assertThat(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()).isSameAs(query);
            assertThat(query.isFocusOwner()).isTrue();
            assertThat(query.getBackground()).as("the switch restyled the field in place").isEqualTo(light.color(SemanticColor.SURFACE_INPUT));
            assertThat(query.getClientProperty("FlatLaf.style")).isEqualTo("borderWidth: 0; focusWidth: 0; innerFocusWidth: 0");
            assertThat(owner[0].chrome().search()).isSameAs(search);
            assertThat(search.getParent()).isSameAs(searchParent);
            assertThat(search.contextText()).isEqualTo("~/projects/jasper");
            assertThat(search.getToolTipText()).isEqualTo("Working directory");
            assertThat(search.collapsed()).isFalse();
            assertThat(search.isFocusable()).as("the title search is reached by its shortcut").isFalse();
            assertThat(owner[0].toolbar().getBackground()).as("the chrome switch reached the window").isEqualTo(light.color(SemanticColor.SURFACE_APP));
        });
    }
}
