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
import java.awt.Rectangle;
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
        var titleBar = NativeShells.followTitleBar(window, root, dialog);
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

    /** Plan 14 (Rulings 19): under macOS's band or Windows' classic title bar the layer starts below it, and a centred dialog stays centred on the whole window. */
    @Test void classicDialogsStartBelowTheClassicTitle() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        dev.jasper.app.platform.TitleInset.publish(root, () -> 28);
        var body = new JPanel(); body.setPreferredSize(new Dimension(400, 201));
        var centred = new ClassicDialog(KitTestSupport.context(), body, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
        var unlock = WindowOverlay.classic(root, centred);
        var window = new JPanel();
        var titleBar = NativeShells.followTitleBar(window, root, centred);
        try {
            unlock.show();
            titleBar.componentResized(new ComponentEvent(window, ComponentEvent.COMPONENT_RESIZED));
            assertThat(centred.getBounds()).as("the layer leaves the title chrome uncovered").isEqualTo(new Rectangle(0, 28, 800, 572));
            int height = unlock.cardBounds().height;
            assertThat(28 + unlock.cardBounds().y).as("centred on the whole window").isEqualTo((600 - height) / 2);
        } finally { unlock.close(); }
        var sheetRoot = new JRootPane(); sheetRoot.getLayeredPane().setSize(800, 600);
        dev.jasper.app.platform.TitleInset.publish(sheetRoot, () -> 28);
        var sheetBody = new JPanel(); sheetBody.setPreferredSize(new Dimension(440, 300));
        var sheet = new ClassicDialog(KitTestSupport.context(), sheetBody, "Add Login", Nodes.Hue.BLUE, ClassicDialog.Placement.SHEET);
        var addLogin = WindowOverlay.classic(sheetRoot, sheet);
        try {
            addLogin.show();
            assertThat(sheet.getY()).as("joined under the title chrome").isEqualTo(28);
            assertThat(addLogin.cardBounds().y).isZero();
        } finally { addLogin.close(); }
    }

    /** Review Focus 2: the title inset can change after the last resize (FlatLaf republishes full screen late); the layer and the card follow it. */
    @Test void theLayerAndTheCardFollowATitleInsetChangeWithoutAResize() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        int[] inset = {28};
        dev.jasper.app.platform.TitleInset.publish(root, () -> inset[0]);
        var body = new JPanel(); body.setPreferredSize(new Dimension(400, 201));
        var centred = new ClassicDialog(KitTestSupport.context(), body, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
        var unlock = WindowOverlay.classic(root, centred);
        var window = new JPanel();
        var titleBar = NativeShells.followTitleBar(window, root, centred);
        root.addPropertyChangeListener(dev.jasper.app.platform.TitleInset.CHANGED, titleBar);
        try {
            unlock.show();
            titleBar.componentResized(new ComponentEvent(window, ComponentEvent.COMPONENT_RESIZED));
            int height = unlock.cardBounds().height;
            assertThat(centred.getY()).isEqualTo(28);
            inset[0] = 0;
            dev.jasper.app.platform.TitleInset.changed(root, 28);
            assertThat(centred.getBounds()).as("full screen: the layer covers the window").isEqualTo(new Rectangle(0, 0, 800, 600));
            assertThat(unlock.cardBounds().y).as("still centred on the whole window").isEqualTo((600 - height) / 2);
            inset[0] = 28;
            dev.jasper.app.platform.TitleInset.changed(root, 0);
            assertThat(centred.getY()).as("back below the band").isEqualTo(28);
            assertThat(28 + unlock.cardBounds().y).isEqualTo((600 - height) / 2);
        } finally { unlock.close(); root.removePropertyChangeListener(dev.jasper.app.platform.TitleInset.CHANGED, titleBar); }
    }
}
