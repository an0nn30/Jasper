package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.platform.WindowInput;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.ClassicDialog;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Review Focus 4: classic dialogs over one window stack, route keys to the newest and hand them back. */
@ExtendWith(EdtTestExtension.class)
class ClassicWindowOverlayTest {
    static KeyEvent key(JComponent source, int code) { return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED); }

    /** Where {@code WindowOverlay.focus} puts the focus in a dialog: its card's default component. */
    static Component focusTarget(ClassicDialog dialog) {
        var card = dialog.card();
        return card.getFocusTraversalPolicy().getDefaultComponent(card);
    }

    @Test void classicDialogsStackAndHandTheKeysBack() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        root.setContentPane(new JTextField());
        // Displayable, as in a window, so the cards' focus policies accept their controls.
        var host = new JPanel(new BorderLayout()); host.add(root); host.addNotify();
        var context = KitTestSupport.context();
        var sheetBody = new JPanel(); var name = new JTextField(); sheetBody.add(name); sheetBody.setPreferredSize(new Dimension(440, 300));
        var unlockBody = new JPanel(); var password = new JTextField(); unlockBody.add(password);
        var sheet = new ClassicDialog(context, sheetBody, "Add Login", Nodes.Hue.BLUE, ClassicDialog.Placement.SHEET);
        var centred = new ClassicDialog(context, unlockBody, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
        var addLogin = WindowOverlay.classic(root, sheet);
        var unlock = WindowOverlay.classic(root, centred);
        try {
            addLogin.show();
            assertThat(addLogin.cardBounds().y).as("the sheet joins the top").isZero();
            assertThat(focusTarget(sheet)).as("the sheet's focus goes to its first field, not its strip").isSameAs(name);
            unlock.show();
            assertThat(root.getClientProperty(WindowInput.OVERLAY)).isSameAs(unlock);
            assertThat(focusTarget(centred)).as("the newest takes the focus in its content, where typing goes").isSameAs(password);
            assertThat(unlock.filterKey(key(password, KeyEvent.VK_ESCAPE))).as("Escape reaches its Cancel").isFalse();
            assertThat(unlock.filterKey(key(name, KeyEvent.VK_A))).as("the dialog below is blocked").isTrue();
            unlock.close();
            assertThat(unlockBody.getParent()).as("gone at once, with no fade").isNull();
            assertThat(root.getClientProperty(WindowInput.OVERLAY)).isSameAs(addLogin);
            assertThat(addLogin.filterKey(key(name, KeyEvent.VK_A))).isFalse();
        } finally { unlock.close(); addLogin.close(); }
        assertThat(WindowInput.blocked(root)).isFalse();
        assertThat(root.getClientProperty("Jasper.windowOverlays")).isNull();
    }

    /** A centred dialog stays centred on its whole window when the window's title bar changes (macOS full screen hides it). */
    @Test void aCentredDialogFollowsItsWindowsTitleBar() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        var body = new JPanel(); body.setPreferredSize(new Dimension(400, 201));
        var dialog = new ClassicDialog(KitTestSupport.context(), body, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
        var unlock = WindowOverlay.classic(root, dialog);
        // The owner window's title bar is its top inset; a panel's border stands in for it.
        var window = new JPanel(); window.setBorder(new EmptyBorder(28, 0, 0, 0));
        var titleBar = NativeShells.followTitleBar(window, dialog);
        try {
            unlock.show();
            titleBar.componentResized(new ComponentEvent(window, ComponentEvent.COMPONENT_RESIZED));
            int height = unlock.cardBounds().height;
            assertThat(unlock.cardBounds().y).as("centred with the title bar").isEqualTo((600 - 28 - height) / 2);
            window.setBorder(new EmptyBorder(0, 0, 0, 0));
            titleBar.componentResized(new ComponentEvent(window, ComponentEvent.COMPONENT_RESIZED));
            assertThat(unlock.cardBounds().y).as("re-centred once the title bar is hidden").isEqualTo((600 - height) / 2);
        } finally { unlock.close(); }
    }
}
