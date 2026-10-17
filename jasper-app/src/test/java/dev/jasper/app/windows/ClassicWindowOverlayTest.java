package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.platform.WindowInput;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.ClassicDialog;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Review Focus 4: classic dialogs over one window stack, route keys to the newest and hand them back. */
@ExtendWith(EdtTestExtension.class)
class ClassicWindowOverlayTest {
    static KeyEvent key(JComponent source, int code) { return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED); }

    @Test void classicDialogsStackAndHandTheKeysBack() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        root.setContentPane(new JTextField());
        var context = KitTestSupport.context();
        var sheetBody = new JPanel(); var name = new JTextField(); sheetBody.add(name); sheetBody.setPreferredSize(new Dimension(440, 300));
        var unlockBody = new JPanel(); var password = new JTextField(); unlockBody.add(password);
        var addLogin = WindowOverlay.classic(root, new ClassicDialog(context, sheetBody, "Add Login", Nodes.Hue.BLUE, ClassicDialog.Placement.SHEET));
        var unlock = WindowOverlay.classic(root, new ClassicDialog(context, unlockBody, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED));
        try {
            addLogin.show();
            assertThat(addLogin.cardBounds().y).as("the sheet joins the top").isZero();
            unlock.show();
            assertThat(root.getClientProperty(WindowInput.OVERLAY)).isSameAs(unlock);
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
}
